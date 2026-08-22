package com.chessplatform.puzzle;

import com.chessplatform.bot.EngineEvaluation;
import com.chessplatform.bot.StockfishEngine;
import com.chessplatform.bot.StockfishEngineFactory;
import com.chessplatform.persistence.GameReplayService;
import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.Puzzle;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.PuzzleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PuzzleGenerationServiceTest {

    @Mock
    private PuzzleRepository puzzleRepository;

    @Mock
    private StockfishEngineFactory engineFactory;

    @Mock
    private StockfishEngine engine;

    // Real, no mock — es lógica pura de reproducir jugadas sobre un tablero, igual que
    // ya se reutiliza tal cual en otros tests de este proyecto (p. ej.
    // UserRatingService dentro de GameResultRecorderTest).
    private final GameReplayService gameReplayService = new GameReplayService();

    private static void setId(Game game, String id) {
        try {
            Field field = Game.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(game, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Game gameWithMoves(String moveList) {
        Game game = new Game(new User("white", "hash"), new User("black", "hash"), "5+3");
        setId(game, "game-id");
        game.setResult("1-0");
        game.setMoveList(moveList);
        return game;
    }

    private PuzzleGenerationService newService(String stockfishPath) {
        return new PuzzleGenerationService(puzzleRepository, gameReplayService, engineFactory, stockfishPath);
    }

    @Test
    void generateFromGameDoesNothingWhenStockfishIsNotConfigured() {
        PuzzleGenerationService service = newService(""); // vacío == sin configurar

        service.generateFromGame(gameWithMoves("e2e4 e7e5"));

        verify(puzzleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateFromGameDoesNothingForAGameWithoutAnyMoves() {
        PuzzleGenerationService service = newService("/usr/games/stockfish");

        service.generateFromGame(gameWithMoves(""));

        verify(puzzleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateFromGameDoesNothingWhenTheEngineFailsToStart() throws IOException {
        PuzzleGenerationService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenThrow(new IOException("no se pudo arrancar"));

        // No debería propagar la excepción — un fallo del motor no debería afectar a
        // nada más, ver el javadoc de la clase.
        service.generateFromGame(gameWithMoves("e2e4 e7e5"));

        verify(puzzleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateFromGameSavesNothingWhenNoSwingIsBigEnough() throws IOException {
        PuzzleGenerationService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);
        // Tres posiciones (inicial + 2 jugadas), swings pequeños entre todas — nada
        // supera el umbral de 300 centipawns.
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 20, null),
                new EngineEvaluation(null, 10, null),
                new EngineEvaluation(null, 30, null)
        );

        service.generateFromGame(gameWithMoves("e2e4 e7e5"));

        verify(puzzleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateFromGameSavesAPuzzleWhenABigSwingIsDetected() throws IOException {
        PuzzleGenerationService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);
        // Posición inicial: 0 (blancas). Tras e2e4: -20 desde el punto de vista de
        // negras (normal, blancas ligeramente mejor). Tras e7e5: +500 desde el punto de
        // vista de blancas — un swing enorme a favor de blancas, negras acaban de
        // cometer "el error" en este montaje sintético. Cuarta llamada: la
        // re-evaluación de esa misma posición para sacar la jugada solución.
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 0, null),
                new EngineEvaluation(null, -20, null),
                new EngineEvaluation(null, 500, null),
                new EngineEvaluation("d1h5", 500, null)
        );

        service.generateFromGame(gameWithMoves("e2e4 e7e5"));

        ArgumentCaptor<Puzzle> saved = ArgumentCaptor.forClass(Puzzle.class);
        verify(puzzleRepository).save(saved.capture());
        assertThat(saved.getValue().getSourceGameId()).isEqualTo("game-id");
        assertThat(saved.getValue().getSideToMove()).isEqualTo("white"); // le toca a blancas castigar el error
        assertThat(saved.getValue().getSolutionUci()).isEqualTo("d1h5");
        // Calculadas con el motor de reglas real (Board/Move), no con Stockfish
        // mockeado — deberían ser las jugadas legales de verdad tras 1.e4 e5.
        assertThat(saved.getValue().getLegalMovesUci()).isNotBlank();
    }

    @Test
    void generateFromGameKeepsOnlyTheBiggestSwingWhenThereAreSeveralCandidates() throws IOException {
        PuzzleGenerationService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);

        // Ajustamos las evaluaciones para simular errores reales del jugador en turno:
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 0, null),    // 0: Posición inicial
                new EngineEvaluation(null, 400, null),  // 1: Tras e2e4 (eval rival es +400 -> drop de 400 para blancas, supera 300)
                new EngineEvaluation(null, -200, null), // 2: Tras e7e5 (recupera ventaja)
                new EngineEvaluation(null, 700, null),  // 3: Tras g1f3 (eval rival es +700 -> drop mayor de 900 para blancas)
                new EngineEvaluation("g1f3", -600, null) // 4: Solución tras confirmar el swing de la pos 3
        );

        service.generateFromGame(gameWithMoves("e2e4 e7e5 g1f3"));

        ArgumentCaptor<Puzzle> saved = ArgumentCaptor.forClass(Puzzle.class);
        verify(puzzleRepository).save(saved.capture());
        assertThat(saved.getValue().getSolutionUci()).isEqualTo("g1f3");
    }

    @Test
    void generateFromGameDoesNothingWhenTheEngineThrowsMidAnalysis() throws IOException {
        PuzzleGenerationService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);
        when(engine.evaluate(anyString(), anyInt()))
                .thenThrow(new IOException("Stockfish dejó de responder"));

        service.generateFromGame(gameWithMoves("e2e4 e7e5"));

        verify(puzzleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateFromGameAlwaysClosesTheEngineAfterwards() throws IOException {
        PuzzleGenerationService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 0, null),
                new EngineEvaluation(null, 0, null),
                new EngineEvaluation(null, 0, null)
        );

        service.generateFromGame(gameWithMoves("e2e4 e7e5"));

        // StockfishEngine implementa AutoCloseable — el try-with-resources de
        // generateFromGame() debería cerrarlo siempre, incluso sin haber encontrado
        // ningún puzzle.
        verify(engine).close();
    }
}
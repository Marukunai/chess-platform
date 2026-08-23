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
        return gameWithMoves(moveList, "resignation"); // motivo neutro por defecto para los tests que no les importa
    }

    private static Game gameWithMoves(String moveList, String reason) {
        Game game = new Game(new User("white", "hash"), new User("black", "hash"), "5+3");
        setId(game, "game-id");
        game.setResult("1-0");
        game.setReason(reason);
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
        // cometer "el error" en este montaje sintético. Las dos últimas son de
        // buildSolutionLine: la jugada solución (Dh5, legal de verdad desde esta
        // posición) y la evaluación de después, que aquí se mantiene modesta a
        // propósito para que la línea se corte en una sola jugada.
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 0, null),
                new EngineEvaluation(null, -20, null),
                new EngineEvaluation(null, 500, null),
                new EngineEvaluation("d1h5", 500, null),
                new EngineEvaluation(null, 100, null)
        );

        service.generateFromGame(gameWithMoves("e2e4 e7e5"));

        ArgumentCaptor<Puzzle> saved = ArgumentCaptor.forClass(Puzzle.class);
        verify(puzzleRepository).save(saved.capture());
        assertThat(saved.getValue().getSourceGameId()).isEqualTo("game-id");
        assertThat(saved.getValue().getSideToMove()).isEqualTo("white"); // le toca a blancas castigar el error
        assertThat(saved.getValue().getSolutionUci()).isEqualTo("d1h5"); // una sola jugada — la ventaja tras jugarla no es lo bastante clara como para forzar otra
        // Calculadas con el motor de reglas real (Board/Move), no con Stockfish
        // mockeado — deberían ser las jugadas legales de verdad tras 1.e4 e5.
        assertThat(saved.getValue().getLegalMovesUci()).isNotBlank();
    }

    @Test
    void generateFromGameKeepsOnlyTheBiggestSwingWhenThereAreSeveralCandidates() throws IOException {
        PuzzleGenerationService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);
        // Cuatro posiciones (inicial + 3 jugadas). Con drop_k = eval(k-1) + eval(k) (la
        // conversión de perspectiva se cancela algebraicamente en la resta de
        // toComparableScore/-toComparableScore, ver el código): drop_1 = 0+350=350
        // (primer candidato, supera el umbral), drop_2 = 350-300=50 (no supera),
        // drop_3 = -300+1150=850 (el más grande de los dos, este es el que debería
        // quedarse). Las dos últimas son de buildSolutionLine, con la ventaja tras la
        // jugada solución mantenida modesta a propósito para que la línea no se alargue.
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 0, null),      // posición inicial
                new EngineEvaluation(null, 350, null),    // tras jugada 1 — drop de 350, primer candidato
                new EngineEvaluation(null, -300, null),   // tras jugada 2 — drop de 50, no supera el umbral
                new EngineEvaluation(null, 1150, null),   // tras jugada 3 — drop de 850, el más grande
                new EngineEvaluation("d8h4", 1150, null), // solución de esa posición (Dh4, legal de verdad tras 1.e4 e5 2.Cf3)
                new EngineEvaluation(null, 50, null)      // evaluación tras jugarla — modesta, corta la línea en una sola jugada
        );

        service.generateFromGame(gameWithMoves("e2e4 e7e5 g1f3"));

        ArgumentCaptor<Puzzle> saved = ArgumentCaptor.forClass(Puzzle.class);
        verify(puzzleRepository).save(saved.capture());
        assertThat(saved.getValue().getSolutionUci()).isEqualTo("d8h4");
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

    @Test
    void generateFromGameDoesNotTreatTheFinalCheckmatePositionAsACandidate() throws IOException {
        // El bug real: la jugada de mate siempre es el "swing" más grande de toda la
        // partida (pasar de cualquier evaluación a mate es el salto más alto posible),
        // así que sin esta exclusión, esta posición SIEMPRE ganaría como candidata —
        // pero al no tener ninguna jugada legal, no hay ninguna solución que dar, y el
        // puzzle se descartaría entero aunque hubiera habido un error resoluble antes.
        PuzzleGenerationService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 0, null),   // posición inicial
                new EngineEvaluation(null, -10, null)  // tras jugada 1 — nada especial; nunca debería llegar a evaluarse la jugada 2 (el mate)
        );

        service.generateFromGame(gameWithMoves("e2e4 e7e5", "checkmate"));

        // Solo dos llamadas al motor (posición inicial + tras la jugada 1) — la
        // posición de mate (tras la jugada 2) nunca debería ni evaluarse, porque ya se
        // sabe de antemano (por game.getReason()) que no puede ser un candidato válido.
        verify(engine, org.mockito.Mockito.times(2)).evaluate(anyString(), anyInt());
        verify(puzzleRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void generateFromGameStillFindsAnEarlierBlunderWhenTheGameEndedInCheckmate() throws IOException {
        PuzzleGenerationService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 0, null),     // posición inicial
                new EngineEvaluation(null, -20, null),   // tras 1. e2e4 — normal
                new EngineEvaluation(null, 500, null),   // tras 1... e7e5 — AQUÍ está el error de verdad
                new EngineEvaluation("d1h5", 500, null), // solución de esa posición (Dh5, legal de verdad aquí)
                new EngineEvaluation(null, 100, null)    // evaluación tras jugarla — modesta, corta la línea en una sola jugada
        );

        // Tres jugadas — la partida "termina en mate" en la tercera, pero el error de
        // verdad está en la segunda. Solo dos posiciones (1 y 2) deberían evaluarse en
        // el bucle principal — la tercera (mate) queda excluida de entrada.
        service.generateFromGame(gameWithMoves("e2e4 e7e5 g1f3", "checkmate"));

        ArgumentCaptor<Puzzle> saved = ArgumentCaptor.forClass(Puzzle.class);
        verify(puzzleRepository).save(saved.capture());
        assertThat(saved.getValue().getSolutionUci()).isEqualTo("d1h5");
    }

    @Test
    void generateFromGameExtendsTheLineWhenTheAdvantageStaysClearAfterTheFirstMove() throws IOException {
        PuzzleGenerationService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);
        // Igual que el test anterior hasta encontrar el swing, pero esta vez la
        // evaluación TRAS la jugada solución se mantiene enorme (el rival sigue
        // claramente perdiendo) — debería añadir la respuesta del rival a la línea, en
        // vez de cortarla en una sola jugada. El sexto valor (sin bestMoveUci) hace que
        // el bucle se pare justo al intentar buscar una SEGUNDA jugada del que resuelve
        // — así la línea queda en exactamente 2 jugadas (la propia + la respuesta),
        // sin depender de si el motor encontraría o no una tercera de verdad.
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 0, null),
                new EngineEvaluation(null, -20, null),
                new EngineEvaluation(null, 500, null),
                new EngineEvaluation("d1h5", 500, null),   // primera jugada del que resuelve (Dh5)
                new EngineEvaluation("g8f6", -600, null),  // tras Dh5, negras siguen muy perdidas (-600 desde su perspectiva) -> se extiende; g8f6 es la respuesta del rival (Cf6, legal de verdad aquí)
                new EngineEvaluation(null, 50, null)       // sin bestMoveUci -> el bucle se corta al buscar una segunda jugada del que resuelve
        );

        service.generateFromGame(gameWithMoves("e2e4 e7e5 g1f3", "checkmate"));

        ArgumentCaptor<Puzzle> saved = ArgumentCaptor.forClass(Puzzle.class);
        verify(puzzleRepository).save(saved.capture());
        // Dos jugadas en la línea: la del que resuelve (Dh5) y la respuesta forzada del
        // rival (Cf6) — a diferencia de los otros tests, donde se corta en una sola.
        assertThat(saved.getValue().getSolutionUci()).isEqualTo("d1h5 g8f6");
    }
}
package com.chessplatform.puzzle;

import com.chessplatform.bot.StockfishEngine;
import com.chessplatform.bot.StockfishEngineFactory;
import com.chessplatform.persistence.entity.Puzzle;
import com.chessplatform.persistence.repository.PuzzleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockea PuzzleGenerationService directamente, no StockfishEngine/StockfishEngineFactory
 * por debajo — así estos tests comprueban el comportamiento propio de PuzzleSeeder (qué
 * secuencias intenta, qué pasa si una falla, que lo que se guarda no lleve
 * sourceGameId...) sin depender de que las jugadas de mentira usadas en el montaje sean
 * legales de verdad sobre un tablero real. Esa legalidad (aplicar cada jugada y no
 * reventar) ya se comprueba aparte en everySeedSequenceIsSyntacticallyPlayableFromTheInitialPosition,
 * y el análisis en sí ya está probado a fondo en PuzzleGenerationServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class PuzzleSeederTest {

    @Mock
    private PuzzleRepository puzzleRepository;

    @Mock
    private PuzzleGenerationService puzzleGenerationService;

    @Mock
    private StockfishEngineFactory engineFactory;

    @Mock
    private StockfishEngine engine;

    private PuzzleSeeder newSeeder(String stockfishPath) {
        return new PuzzleSeeder(puzzleRepository, puzzleGenerationService, engineFactory, stockfishPath);
    }

    private static Puzzle canned() {
        return new Puzzle(null, "fen", "white", "e2e4", "e2e4 d2d4", "");
    }

    @Test
    void seedPuzzlesDoesNothingWhenStockfishIsNotConfigured() {
        PuzzleSeeder seeder = newSeeder("");

        seeder.seedPuzzles();

        verify(puzzleRepository, never()).save(any());
    }

    @Test
    void seedPuzzlesDoesNothingWhenAlreadySeededBefore() throws IOException {
        PuzzleSeeder seeder = newSeeder("/usr/games/stockfish");
        when(puzzleRepository.existsBySourceGameIdIsNull()).thenReturn(true);

        seeder.seedPuzzles();

        verify(engineFactory, never()).create(anyString());
        verify(puzzleRepository, never()).save(any());
    }

    @Test
    void seedPuzzlesSavesEveryPuzzleThatAnalyzeFinds() throws IOException {
        PuzzleSeeder seeder = newSeeder("/usr/games/stockfish");
        when(puzzleRepository.existsBySourceGameIdIsNull()).thenReturn(false);
        when(engineFactory.create(anyString())).thenReturn(engine);
        when(puzzleGenerationService.analyze(anyString(), anyString(), isNull(), any()))
                .thenReturn(Optional.of(canned()));

        seeder.seedPuzzles();

        // Diez secuencias sembradas, cada una encuentra un puzzle -> diez guardados.
        verify(puzzleRepository, times(10)).save(any(Puzzle.class));
    }

    @Test
    void seedPuzzlesDoesNotSaveAnythingForASequenceWithNoBigEnoughSwing() throws IOException {
        PuzzleSeeder seeder = newSeeder("/usr/games/stockfish");
        when(puzzleRepository.existsBySourceGameIdIsNull()).thenReturn(false);
        when(engineFactory.create(anyString())).thenReturn(engine);
        when(puzzleGenerationService.analyze(anyString(), anyString(), isNull(), any()))
                .thenReturn(Optional.empty());

        seeder.seedPuzzles();

        verify(puzzleRepository, never()).save(any());
    }

    @Test
    void seedPuzzlesTriesTheNextSequenceWhenOneFails() throws IOException {
        PuzzleSeeder seeder = newSeeder("/usr/games/stockfish");
        when(puzzleRepository.existsBySourceGameIdIsNull()).thenReturn(false);
        // La primera vez que se pide un motor, falla; las siguientes, funciona — con
        // diez secuencias sembradas, esto simula que la primera falla y las demás
        // deberían intentarse igualmente.
        when(engineFactory.create(anyString()))
                .thenThrow(new IOException("fallo simulado"))
                .thenReturn(engine);
        when(puzzleGenerationService.analyze(anyString(), anyString(), isNull(), any()))
                .thenReturn(Optional.of(canned()));

        // No debería propagar la excepción de la primera secuencia.
        seeder.seedPuzzles();

        verify(engineFactory, times(10)).create(anyString());
        // Solo nueve de las diez llegan a pedir el análisis — la primera se cae al
        // arrancar el motor, antes de eso.
        verify(puzzleRepository, times(9)).save(any(Puzzle.class));
    }

    @Test
    void seedPuzzlesTriesTheNextSequenceWhenAnalyzeThrows() throws IOException {
        PuzzleSeeder seeder = newSeeder("/usr/games/stockfish");
        when(puzzleRepository.existsBySourceGameIdIsNull()).thenReturn(false);
        when(engineFactory.create(anyString())).thenReturn(engine);
        when(puzzleGenerationService.analyze(anyString(), anyString(), isNull(), any()))
                .thenThrow(new IOException("el motor dejó de responder"))
                .thenReturn(Optional.of(canned()));

        // No debería propagar la excepción de la primera secuencia que falla.
        seeder.seedPuzzles();

        verify(puzzleRepository, times(9)).save(any(Puzzle.class));
    }

    @Test
    void seedPuzzlesPassesNullAsTheSourceGameId() throws IOException {
        PuzzleSeeder seeder = newSeeder("/usr/games/stockfish");
        when(puzzleRepository.existsBySourceGameIdIsNull()).thenReturn(false);
        when(engineFactory.create(anyString())).thenReturn(engine);
        when(puzzleGenerationService.analyze(anyString(), anyString(), isNull(), any()))
                .thenReturn(Optional.of(canned()));

        seeder.seedPuzzles();

        // El propio matcher isNull() en el stub ya lo comprueba (si se llamara con un
        // sourceGameId no nulo, el stub no aplicaría y devolvería null, reventando el
        // resto) — esta comprobación adicional confirma además que lo guardado
        // tampoco lleva sourceGameId.
        ArgumentCaptor<Puzzle> saved = ArgumentCaptor.forClass(Puzzle.class);
        verify(puzzleRepository, times(10)).save(saved.capture());
        for (Puzzle puzzle : saved.getAllValues()) {
            assertThat(puzzle.getSourceGameId()).isNull();
        }
    }

    @Test
    void everySeedSequenceIsSyntacticallyPlayableFromTheInitialPosition() {
        // No confirma legalidad completa (Board.applyMove() tampoco lo hace, ver su
        // javadoc), pero SÍ confirma que cada jugada tiene una pieza de verdad en su
        // casilla de origen — el error más probable al convertir a mano una apertura
        // conocida a UCI (una casilla mal escrita, una jugada de más o de menos). Sin
        // esto, un error así no daría ningún fallo visible: se guardaría un puzzle con
        // una posición corrupta camuflada de válida.
        for (String moveList : PuzzleSeeder.seedMoveListsForTesting()) {
            com.chessplatform.engine.Board board = com.chessplatform.engine.Board.initial();
            for (String moveUci : moveList.trim().split(" ")) {
                com.chessplatform.engine.Move move = com.chessplatform.engine.Move.fromUci(moveUci);
                assertDoesNotThrow(() -> board.applyMove(move),
                        () -> "Jugada sin pieza de origen real: " + moveUci + " en la secuencia: " + moveList);
            }
        }
    }
}
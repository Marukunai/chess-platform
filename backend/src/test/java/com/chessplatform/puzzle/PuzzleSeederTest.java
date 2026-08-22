package com.chessplatform.puzzle;

import com.chessplatform.bot.StockfishEngine;
import com.chessplatform.bot.StockfishEngineFactory;
import com.chessplatform.persistence.GameReplayService;
import com.chessplatform.persistence.entity.Puzzle;
import com.chessplatform.persistence.repository.PuzzleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PuzzleSeederTest {

    @Mock
    private PuzzleRepository puzzleRepository;

    @Mock
    private StockfishEngineFactory engineFactory;

    @Mock
    private StockfishEngine engine;

    // Real, no mock — PuzzleGenerationService.analyze() es lógica pura sobre el
    // resultado del motor (mockeado aparte), no hace falta doblarla también.
    private PuzzleGenerationService puzzleGenerationService;

    @BeforeEach
    void setUp() {
        puzzleGenerationService = new PuzzleGenerationService(puzzleRepository, new GameReplayService(),
                engineFactory, "/usr/games/stockfish");
    }

    private PuzzleSeeder newSeeder(String stockfishPath) {
        return new PuzzleSeeder(puzzleRepository, puzzleGenerationService, engineFactory, stockfishPath);
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
    void seedPuzzlesSavesPuzzlesWithoutASourceGame() throws IOException {
        PuzzleSeeder seeder = newSeeder("/usr/games/stockfish");
        when(puzzleRepository.existsBySourceGameIdIsNull()).thenReturn(false);
        when(engineFactory.create(anyString())).thenReturn(engine);
        // Cualquier evaluación no-cero grande, en cualquier orden, es suficiente para
        // que ALGUNA de las dos secuencias sembradas produzca un swing por encima del
        // umbral — no hace falta reproducir el cálculo exacto aquí, ese ya está
        // probado a fondo en PuzzleGenerationServiceTest.
        when(engine.evaluate(anyString(), anyInt()))
                .thenReturn(new com.chessplatform.bot.EngineEvaluation("e2e4", 900, null));

        seeder.seedPuzzles();

        verify(puzzleRepository, org.mockito.Mockito.atLeastOnce()).save(any(Puzzle.class));
    }

    @Test
    void seedPuzzlesTriesTheNextSequenceWhenOneFails() throws IOException {
        PuzzleSeeder seeder = newSeeder("/usr/games/stockfish");
        when(puzzleRepository.existsBySourceGameIdIsNull()).thenReturn(false);
        // La primera vez que se pide un motor, falla; las siguientes, funciona — con
        // dos secuencias sembradas, esto simula que la primera falla y la segunda
        // debería intentarse igualmente.
        when(engineFactory.create(anyString()))
                .thenThrow(new IOException("fallo simulado"))
                .thenReturn(engine);
        when(engine.evaluate(anyString(), anyInt()))
                .thenReturn(new com.chessplatform.bot.EngineEvaluation("e2e4", 900, null));

        // No debería propagar la excepción de la primera secuencia.
        seeder.seedPuzzles();

        verify(engineFactory, org.mockito.Mockito.times(2)).create(anyString());
    }

    @Test
    void seedPuzzlesSavedPuzzlesHaveNoSourceGame() throws IOException {
        PuzzleSeeder seeder = newSeeder("/usr/games/stockfish");
        when(puzzleRepository.existsBySourceGameIdIsNull()).thenReturn(false);
        when(engineFactory.create(anyString())).thenReturn(engine);
        when(engine.evaluate(anyString(), anyInt()))
                .thenReturn(new com.chessplatform.bot.EngineEvaluation("e2e4", 900, null));

        seeder.seedPuzzles();

        org.mockito.ArgumentCaptor<Puzzle> saved = org.mockito.ArgumentCaptor.forClass(Puzzle.class);
        verify(puzzleRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThatAllSavedPuzzlesHaveNoSourceGame(saved.getAllValues());
    }

    private void assertThatAllSavedPuzzlesHaveNoSourceGame(java.util.List<Puzzle> puzzles) {
        for (Puzzle puzzle : puzzles) {
            org.assertj.core.api.Assertions.assertThat(puzzle.getSourceGameId()).isNull();
        }
    }
}
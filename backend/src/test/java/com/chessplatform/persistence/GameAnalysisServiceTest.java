package com.chessplatform.persistence;

import com.chessplatform.bot.EngineEvaluation;
import com.chessplatform.bot.StockfishEngine;
import com.chessplatform.bot.StockfishEngineFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameAnalysisServiceTest {

    @Mock
    private StockfishEngineFactory engineFactory;

    @Mock
    private StockfishEngine engine;

    // Real, no mock — es lógica pura de reproducir jugadas sobre un tablero, igual que
    // ya se reutiliza en otros tests de este proyecto.
    private final GameReplayService gameReplayService = new GameReplayService();

    private GameAnalysisService newService(String stockfishPath) {
        return new GameAnalysisService(gameReplayService, engineFactory, stockfishPath);
    }

    @Test
    void isAvailableIsFalseWhenStockfishPathIsBlank() {
        assertThat(newService("").isAvailable()).isFalse();
        assertThat(newService(null).isAvailable()).isFalse();
    }

    @Test
    void isAvailableIsTrueWhenStockfishPathIsConfigured() {
        assertThat(newService("/usr/games/stockfish").isAvailable()).isTrue();
    }

    @Test
    void analyzeReturnsAnEmptyListForAGameWithNoMoves() throws IOException {
        GameAnalysisService service = newService("/usr/games/stockfish");

        assertThat(service.analyze("")).isEmpty();
        assertThat(service.analyze(null)).isEmpty();
    }

    @Test
    void analyzeConvertsEvaluationsToWhitesPerspectiveConsistently() throws IOException {
        GameAnalysisService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);
        // e0=0 (inicial, blancas por mover). e1=-50 (tras 1.e4, negras por mover — el
        // motor lo da desde SU punto de vista, negras se ven algo peor, lo cual es
        // justo lo mismo que "blancas +50" desde la perspectiva de blancas).
        // e2=30 (tras 1...e5, blancas por mover otra vez — el motor ya lo da desde el
        // punto de vista de blancas directamente, sin que haga falta invertir nada).
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 0, null),
                new EngineEvaluation(null, -50, null),
                new EngineEvaluation(null, 30, null)
        );

        List<GameAnalysisService.MoveAnalysis> analysis = service.analyze("e2e4 e7e5");

        assertThat(analysis).hasSize(2);
        // Jugada 1 (1.e4, de blancas): el eval que llega tras aplicarla está desde el
        // punto de vista de NEGRAS (les toca mover) — hay que invertir el signo.
        assertThat(analysis.get(0).evalCentipawns()).isEqualTo(50);
        // Jugada 2 (1...e5, de negras): el eval que llega tras aplicarla ya está desde
        // el punto de vista de BLANCAS (les toca mover) — no hace falta invertir nada.
        assertThat(analysis.get(1).evalCentipawns()).isEqualTo(30);
    }

    @Test
    void analyzeClassifiesASmallOrNegativeDropAsBest() throws IOException {
        GameAnalysisService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);
        // drop_1 = e0+e1 = 0+(-50) = -50 (mejora la posición, no es un problema)
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 0, null),
                new EngineEvaluation(null, -50, null)
        );

        List<GameAnalysisService.MoveAnalysis> analysis = service.analyze("e2e4");

        assertThat(analysis.get(0).classification()).isEqualTo("best");
    }

    @Test
    void analyzeClassifiesABigDropAsBlunder() throws IOException {
        GameAnalysisService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);
        // drop_1 = e0+e1 = 0+300 = 300 >= 250 -> blunder
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 0, null),
                new EngineEvaluation(null, 300, null)
        );

        List<GameAnalysisService.MoveAnalysis> analysis = service.analyze("e2e4");

        assertThat(analysis.get(0).classification()).isEqualTo("blunder");
    }

    @Test
    void analyzeClassifiesAModerateDropAsMistake() throws IOException {
        GameAnalysisService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);
        // drop_1 = 0+150 = 150 -> entre MISTAKE_THRESHOLD(100) y BLUNDER_THRESHOLD(250)
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 0, null),
                new EngineEvaluation(null, 150, null)
        );

        List<GameAnalysisService.MoveAnalysis> analysis = service.analyze("e2e4");

        assertThat(analysis.get(0).classification()).isEqualTo("mistake");
    }

    @Test
    void analyzeClassifiesASmallDropAsInaccuracy() throws IOException {
        GameAnalysisService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);
        // drop_1 = 0+70 = 70 -> entre INACCURACY_THRESHOLD(50) y MISTAKE_THRESHOLD(100)
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 0, null),
                new EngineEvaluation(null, 70, null)
        );

        List<GameAnalysisService.MoveAnalysis> analysis = service.analyze("e2e4");

        assertThat(analysis.get(0).classification()).isEqualTo("inaccuracy");
    }

    @Test
    void analyzeIncludesTheNotationForEachMove() throws IOException {
        GameAnalysisService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 0, null),
                new EngineEvaluation(null, 0, null),
                new EngineEvaluation(null, 0, null)
        );

        List<GameAnalysisService.MoveAnalysis> analysis = service.analyze("e2e4 e7e5");

        assertThat(analysis).extracting(GameAnalysisService.MoveAnalysis::notation)
                .containsExactly("e4", "e5");
        assertThat(analysis.get(0).moveNumber()).isEqualTo(1);
        assertThat(analysis.get(1).moveNumber()).isEqualTo(2);
    }

    @Test
    void analyzeAlwaysClosesTheEngineAfterwards() throws IOException {
        GameAnalysisService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenReturn(engine);
        when(engine.evaluate(anyString(), anyInt())).thenReturn(
                new EngineEvaluation(null, 0, null),
                new EngineEvaluation(null, 0, null)
        );

        service.analyze("e2e4");

        verify(engine).close();
    }

    @Test
    void analyzePropagatesIOExceptionWhenTheEngineFailsToStart() throws IOException {
        GameAnalysisService service = newService("/usr/games/stockfish");
        when(engineFactory.create(anyString())).thenThrow(new IOException("fallo simulado"));

        org.junit.jupiter.api.Assertions.assertThrows(IOException.class, () -> service.analyze("e2e4"));
    }
}
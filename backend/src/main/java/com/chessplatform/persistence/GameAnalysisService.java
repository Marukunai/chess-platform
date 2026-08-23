package com.chessplatform.persistence;

import com.chessplatform.bot.EngineEvaluation;
import com.chessplatform.bot.StockfishEngine;
import com.chessplatform.bot.StockfishEngineFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Analiza una partida ya jugada, jugada a jugada, para revisión — a diferencia de
 * PuzzleGenerationService (que solo busca el error más grande de toda la partida y
 * corre en segundo plano tras cada partida), esto evalúa TODAS las posiciones y
 * clasifica CADA jugada, y se pide explícitamente cuando alguien abre la revisión de
 * una partida en concreto — no automático, no en segundo plano.
 *
 * Deliberadamente SÍNCRONO: quien pide la revisión está esperando activamente el
 * resultado (no es una mejora de fondo como los puzzles), así que un endpoint REST
 * normal que tarda varios segundos en responder es el diseño correcto aquí, con el
 * cliente mostrando un estado de carga mientras tanto — no hay ningún resultado
 * intermedio útil que devolver antes de tener el análisis completo.
 *
 * Sin caché a propósito, por ahora: cada petición vuelve a analizar la partida entera
 * desde cero. Es una limitación conocida y aceptada (igual que la rareza de logros sin
 * caché) — el primer sitio a optimizar si esto llegara a tener uso pesado de verdad.
 */
@Service
public class GameAnalysisService {

    // Algo más generoso que el usado para detectar puzzles (300ms) — aquí no hay una
    // partida entera por analizar en segundo plano con margen de sobra, es una petición
    // sobre la que alguien está esperando activamente, así que compensa un pelín más de
    // profundidad por posición a cambio de una espera un poco mayor.
    private static final int EVAL_MOVETIME_MS = 400;

    // Umbrales en centésimas de peón de "cuánto empeoró la posición respecto a la
    // mejor jugada disponible" — mismos rangos que ya usan lichess/chess.com de forma
    // aproximada. Por debajo de INACCURACY se considera una jugada normal, sin marca.
    private static final int BLUNDER_THRESHOLD = 250;
    private static final int MISTAKE_THRESHOLD = 100;
    private static final int INACCURACY_THRESHOLD = 50;

    /**
     * @param moveNumber 1-indexado, incluye ambos colores (jugada 1 = primera de
     *                   blancas, jugada 2 = primera de negras...)
     * @param notation la notación legible de la jugada (p. ej. "Rxf6+")
     * @param evalCentipawns evaluación de la posición TRAS esta jugada, desde el punto
     *                       de vista de blancas siempre (para poder dibujar una barra
     *                       de evaluación consistente sin que cambie de signo según de
     *                       quién sea el turno) — null si hay un mate visto (ver evalMate)
     * @param evalMate jugadas hasta mate forzado, desde el punto de vista de blancas
     *                 (positivo == mate a favor de blancas, negativo == en contra) —
     *                 null si no hay mate detectado en esta posición
     * @param classification "best" | "good" | "inaccuracy" | "mistake" | "blunder" —
     *                       null en la primera jugada, que no tiene "antes" con quien compararse
     */
    public record MoveAnalysis(int moveNumber, String notation, Integer evalCentipawns, Integer evalMate,
                               String classification) {
    }

    private final GameReplayService gameReplayService;
    private final StockfishEngineFactory engineFactory;
    private final String stockfishPath;

    public GameAnalysisService(GameReplayService gameReplayService, StockfishEngineFactory engineFactory,
                               @Value("${stockfish.path:}") String stockfishPath) {
        this.gameReplayService = gameReplayService;
        this.engineFactory = engineFactory;
        this.stockfishPath = stockfishPath;
    }

    public boolean isAvailable() {
        return stockfishPath != null && !stockfishPath.isBlank();
    }

    public List<MoveAnalysis> analyze(String moveList) throws IOException {
        GameReplayService.ReplayResult replay = gameReplayService.reconstructReplay(moveList);
        List<String> positions = replay.fenPositions(); // posición 0 = inicial, posición i = tras la jugada i
        List<String> notation = replay.notation(); // notation.get(i) es la jugada que lleva de positions[i] a positions[i+1]

        List<MoveAnalysis> result = new ArrayList<>();
        if (positions.size() < 2) {
            return result; // partida sin ninguna jugada real
        }

        try (StockfishEngine engine = engineFactory.create(stockfishPath)) {
            // La evaluación de cada posición, siempre desde el punto de vista del que
            // mueve EN ESA posición (así lo da el motor) — se convierte a la
            // perspectiva de blancas al construir cada MoveAnalysis, para que la barra
            // de evaluación en el cliente no tenga que preocuparse de a quién le tocaba.
            EngineEvaluation currentEval = engine.evaluate(positions.get(0), EVAL_MOVETIME_MS);

            for (int i = 1; i < positions.size(); i++) {
                int evalBeforeForMover = toComparableScore(currentEval);
                boolean whiteToMoveBeforeThisMove = (i - 1) % 2 == 0; // posición 0 = blancas por mover, posición 1 = negras por mover...

                currentEval = engine.evaluate(positions.get(i), EVAL_MOVETIME_MS);
                int evalAfterForMover = -toComparableScore(currentEval);

                int drop = evalBeforeForMover - evalAfterForMover; // positivo == empeoró para quien acaba de mover
                String classification = classify(drop);

                // Convertir a la perspectiva de blancas para el resultado —
                // currentEval ya está desde el punto de vista de quien mueve EN LA
                // NUEVA posición (el rival de quien acaba de mover), así que si quien
                // acaba de mover fue blancas, ahora le toca a negras y hay que
                // invertir el signo para expresarlo desde blancas; si acaba de mover
                // negras, currentEval ya está desde la perspectiva de blancas tal cual.
                boolean needsSignFlip = whiteToMoveBeforeThisMove; // blancas acaba de mover -> currentEval está en perspectiva de negras
                Integer evalForWhite = currentEval.centipawns() == null ? null
                        : (needsSignFlip ? -currentEval.centipawns() : currentEval.centipawns());
                Integer mateForWhite = currentEval.mateIn() == null ? null
                        : (needsSignFlip ? -currentEval.mateIn() : currentEval.mateIn());

                result.add(new MoveAnalysis(i, notation.get(i - 1), evalForWhite, mateForWhite, classification));
            }
        }

        return result;
    }

    private String classify(int drop) {
        if (drop >= BLUNDER_THRESHOLD) {
            return "blunder";
        }
        if (drop >= MISTAKE_THRESHOLD) {
            return "mistake";
        }
        if (drop >= INACCURACY_THRESHOLD) {
            return "inaccuracy";
        }
        if (drop <= 10) {
            return "best";
        }
        return "good";
    }

    private int toComparableScore(EngineEvaluation eval) {
        if (eval.mateIn() != null) {
            return eval.mateIn() > 0 ? 100_000 - eval.mateIn() : -100_000 - eval.mateIn();
        }
        return eval.centipawns() != null ? eval.centipawns() : 0;
    }
}
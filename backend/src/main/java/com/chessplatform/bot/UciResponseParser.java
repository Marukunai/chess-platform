package com.chessplatform.bot;

import java.util.function.Predicate;

/**
 * Analiza líneas de texto del protocolo UCI — separado de StockfishEngine (que sí habla
 * con el proceso de verdad) a propósito, para poder testear el análisis del protocolo
 * sin necesitar Stockfish instalado en la máquina que corre los tests. Todo estático y
 * sin estado: no hay nada aquí que dependa de una conversación en curso con un proceso
 * concreto.
 */
public final class UciResponseParser {

    private UciResponseParser() {
    }

    public static final Predicate<String> IS_UCI_OK = line -> "uciok".equals(trim(line));
    public static final Predicate<String> IS_READY_OK = line -> "readyok".equals(trim(line));
    public static final Predicate<String> IS_BEST_MOVE_LINE = line -> line != null && line.startsWith("bestmove");

    /** El resultado de analizar una línea "info ... score ..." — ver parseScore(). Los dos campos son mutuamente excluyentes, igual que en EngineEvaluation. */
    public record ScoreInfo(Integer centipawns, Integer mateIn) {
    }

    /**
     * De una línea típica "info depth 15 ... score cp 120 nodes ..." (o "score mate 3"
     * en vez de "score cp"), extrae la puntuación — o null si la línea no es una línea
     * "info" con puntuación (p. ej. "bestmove ...", o un "info" sin score todavía, que
     * pasa en las primeras líneas de una búsqueda). Un motor manda VARIAS líneas "info"
     * según va profundizando la búsqueda — quien llama se queda con la última que vea
     * antes de "bestmove", que es la más fiable.
     */
    public static ScoreInfo parseScore(String line) {
        if (line == null || !line.startsWith("info") || !line.contains(" score ")) {
            return null;
        }
        String[] parts = line.trim().split("\\s+");
        for (int i = 0; i < parts.length - 2; i++) {
            if (!"score".equals(parts[i])) {
                continue;
            }
            String type = parts[i + 1];
            try {
                int value = Integer.parseInt(parts[i + 2]);
                if ("cp".equals(type)) {
                    return new ScoreInfo(value, null);
                }
                if ("mate".equals(type)) {
                    return new ScoreInfo(null, value);
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * De una línea "bestmove e2e4 ponder e7e5" o "bestmove e2e4", extrae solo el
     * movimiento — nunca la parte de "ponder" (la jugada que el motor sugeriría a
     * continuación, no la usamos aquí). "(none)" — lo que manda Stockfish cuando no hay
     * ninguna jugada legal — se traduce a null, para que quien llama no tenga que saber
     * de esta rareza concreta del protocolo.
     */
    public static String extractBestMove(String bestMoveLine) {
        if (bestMoveLine == null || !bestMoveLine.startsWith("bestmove")) {
            return null;
        }
        String[] parts = bestMoveLine.trim().split("\\s+");
        if (parts.length < 2 || "(none)".equals(parts[1])) {
            return null;
        }
        return parts[1];
    }

    private static String trim(String line) {
        return line == null ? "" : line.trim();
    }
}
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
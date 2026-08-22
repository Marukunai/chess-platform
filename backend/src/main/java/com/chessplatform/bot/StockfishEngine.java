package com.chessplatform.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;

/**
 * Un proceso de Stockfish, gestionado directamente vía su protocolo de texto (UCI) —
 * una instancia por partida contra bot en curso, no un proceso compartido entre
 * partidas. Más simple de razonar (cada partida tiene su propia conversación con el
 * motor, sin coordinación entre partidas concurrentes), y a la escala de un proyecto
 * personal el coste de arrancar un proceso nuevo por partida es insignificante frente a
 * la complejidad de gestionar un pool compartido — el primer sitio a reconsiderar si
 * esto llegara a tener muchísimas partidas contra bot simultáneas de verdad.
 *
 * Se cierra con close() al terminar la partida — ver BotGameRegistry, quien decide
 * cuándo.
 */
public class StockfishEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(StockfishEngine.class);
    private static final long READ_TIMEOUT_MILLIS = 10_000;
    private static final long POLL_INTERVAL_MILLIS = 20;

    private final Process process;
    private final BufferedWriter writer;
    private final BufferedReader reader;

    public StockfishEngine(String stockfishPath) throws IOException {
        this.process = new ProcessBuilder(stockfishPath)
                .redirectErrorStream(true)
                .start();
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        sendCommand("uci");
        waitFor(UciResponseParser.IS_UCI_OK);
    }

    /** skillLevel: 0 (más débil) a 20 (fuerza completa) — ver BotDifficulty. */
    public void setSkillLevel(int skillLevel) throws IOException {
        sendCommand("setoption name Skill Level value " + skillLevel);
        sendCommand("isready");
        waitFor(UciResponseParser.IS_READY_OK);
    }

    /**
     * fen: la posición actual. moveTimeMs: cuánto "piensa" antes de responder.
     * Devuelve el movimiento en notación UCI ("e2e4"), o null si el motor no dio
     * ninguno (posición sin jugadas legales — no debería pasar nunca en la práctica, ya
     * que BotMoveService solo pide un movimiento cuando de verdad le toca mover al bot
     * y la partida sigue en curso, pero se maneja igualmente sin lanzar una excepción
     * por si acaso).
     */
    public String bestMove(String fen, int moveTimeMs) throws IOException {
        sendCommand("position fen " + fen);
        sendCommand("go movetime " + moveTimeMs);
        String bestMoveLine = waitFor(UciResponseParser.IS_BEST_MOVE_LINE);
        return UciResponseParser.extractBestMove(bestMoveLine);
    }

    private void sendCommand(String command) throws IOException {
        writer.write(command);
        writer.newLine();
        writer.flush();
    }

    /**
     * Lee líneas hasta encontrar una que cumpla la condición, con un límite de tiempo —
     * sin esto, un binario de Stockfish mal instalado o un proceso colgado bloquearía
     * el hilo que llama para siempre en vez de fallar con un error claro. Sondeo simple
     * con reader.ready() en vez de un hilo aparte con Future+timeout — más código para
     * un beneficio marginal en un caso ya de por sí raro (Stockfish respondiendo tarde),
     * la espera activa cada 20ms es un compromiso razonable aquí.
     */
    private String waitFor(Predicate<String> condition) throws IOException {
        long deadline = System.currentTimeMillis() + READ_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (!reader.ready()) {
                try {
                    Thread.sleep(POLL_INTERVAL_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrumpido esperando respuesta de Stockfish", e);
                }
                continue;
            }
            String line = reader.readLine();
            if (condition.test(line)) {
                return line;
            }
        }
        throw new IOException("Stockfish no respondió a tiempo (más de " + (READ_TIMEOUT_MILLIS / 1000) + "s)");
    }

    @Override
    public void close() {
        try {
            sendCommand("quit");
        } catch (IOException e) {
            // El proceso puede que ya esté muerto o colgado — no pasa nada, se fuerza
            // el cierre igualmente justo debajo. No merece la pena propagar esto: cerrar
            // un motor que ya no hace falta no debería poder reventar nada más.
            log.debug("No se pudo mandar 'quit' a Stockfish al cerrar (probablemente el proceso ya no respondía)", e);
        }
        process.destroy();
    }
}
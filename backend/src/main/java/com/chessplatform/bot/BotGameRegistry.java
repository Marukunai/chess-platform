package com.chessplatform.bot;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Qué partidas activas son contra un bot, y con qué motor concreto — un StockfishEngine
 * es un proceso de verdad (ver su javadoc), así que esta clase es también quien decide
 * cuándo cerrarlo. ConcurrentHashMap porque el registro se consulta desde el hilo que
 * procesa la jugada del humano y se limpia desde donde sea que termine la partida
 * (jaque mate durante esa misma jugada, o más tarde por timeout/abandono) — no hay
 * ninguna garantía de que ambas cosas pasen en el mismo hilo.
 */
@Component
public class BotGameRegistry {

    private final Map<String, BotGameInfo> games = new ConcurrentHashMap<>();

    public void register(String gameId, BotGameInfo info) {
        games.put(gameId, info);
    }

    public Optional<BotGameInfo> find(String gameId) {
        return Optional.ofNullable(games.get(gameId));
    }

    public boolean isBotGame(String gameId) {
        return games.containsKey(gameId);
    }

    /**
     * Cierra el proceso de Stockfish y quita la partida del registro — hay que llamarlo
     * SIEMPRE que una partida contra bot termine, sea como sea (jaque mate, tablas,
     * abandono, timeout...), o el proceso de Stockfish se queda huérfano corriendo
     * indefinidamente en el servidor. No pasa nada si se llama con un gameId que no
     * está registrado (partida normal entre humanos) — simplemente no hace nada.
     */
    public void remove(String gameId) {
        BotGameInfo info = games.remove(gameId);
        if (info != null) {
            info.engine().close();
        }
    }
}
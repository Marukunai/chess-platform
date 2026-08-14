package com.chessplatform.realtime;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registro en memoria de las partidas activas. Patrón directo del registro de mesas del
 * backend de póker.
 */
@Component
public class GameSessionRegistry {

    private final ConcurrentMap<String, GameSession> activeSessions = new ConcurrentHashMap<>();

    public GameSession create(GameSession session) {
        activeSessions.put(session.gameId(), session);
        return session;
    }

    public Optional<GameSession> find(String gameId) {
        return Optional.ofNullable(activeSessions.get(gameId));
    }

    public void remove(String gameId) {
        activeSessions.remove(gameId);
    }

    public int activeCount() {
        return activeSessions.size();
    }

    /**
     * Copia inmutable de las partidas activas ahora mismo — usado por GameTimeoutService
     * para recorrerlas en su barrido periódico. Es una copia a propósito: quien la
     * recorra puede eliminar partidas del registro real durante la iteración (p. ej. al
     * detectar un timeout) sin arriesgarse a una ConcurrentModificationException.
     */
    public Collection<GameSession> allSessions() {
        return List.copyOf(activeSessions.values());
    }

    /**
     * La partida activa (si hay alguna) donde playerId es blancas o negras. Asume como
     * mucho una partida activa por jugador — nada en el sistema impide todavía que
     * alguien acabe en dos partidas a la vez (p. ej. si volviera a entrar en la cola de
     * matchmaking sin haber terminado la actual), así que esto encontraría solo la
     * primera que aparezca en ese caso límite. Usado por PlayerConnectionListener para
     * saber a qué partida corresponde una conexión/desconexión.
     */
    public Optional<GameSession> findByPlayerId(String playerId) {
        return allSessions().stream()
                .filter(session -> playerId.equals(session.whitePlayerId()) || playerId.equals(session.blackPlayerId()))
                .findFirst();
    }
}
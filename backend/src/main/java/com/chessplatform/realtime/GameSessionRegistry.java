package com.chessplatform.realtime;

import org.springframework.stereotype.Component;

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
}

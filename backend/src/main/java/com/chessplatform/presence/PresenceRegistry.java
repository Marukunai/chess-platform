package com.chessplatform.presence;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quién está conectado ahora mismo, en memoria — mismo patrón que GameSessionRegistry,
 * pero para CUALQUIER usuario conectado, no solo los que están en una partida. El valor
 * es si tiene "no molestar" activado, lo único que hace falta guardar aparte del hecho
 * de estar conectado o no.
 */
@Component
public class PresenceRegistry {

    private final Map<String, Boolean> onlineUsersDnd = new ConcurrentHashMap<>();

    public void markOnline(String userId) {
        onlineUsersDnd.putIfAbsent(userId, Boolean.FALSE);
    }

    public void markOffline(String userId) {
        onlineUsersDnd.remove(userId);
    }

    public boolean isOnline(String userId) {
        return onlineUsersDnd.containsKey(userId);
    }

    /** No hace nada si el usuario no está conectado — no tiene sentido guardar un "no molestar" para alguien offline. */
    public void setDoNotDisturb(String userId, boolean doNotDisturb) {
        onlineUsersDnd.computeIfPresent(userId, (id, current) -> doNotDisturb);
    }

    public boolean isDoNotDisturb(String userId) {
        return Boolean.TRUE.equals(onlineUsersDnd.get(userId));
    }
}
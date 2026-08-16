package com.chessplatform.rematch;

import com.chessplatform.engine.Color;
import com.chessplatform.matchmaking.TimeControl;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ofertas de revancha pendientes — vive fuera de GameSession a propósito, porque para
 * cuando alguien pulsa "Revancha" la partida original ya terminó y su GameSession ya se
 * eliminó del registro (ver GameEndNotifier). Solo una oferta pendiente por
 * destinatario a la vez: proponer una segunda sustituye a la primera, no las acumula.
 */
@Component
public class RematchService {

    /**
     * fromColorInRematch/toColorInRematch: los colores YA INTERCAMBIADOS respecto a la
     * partida anterior — quien perdió con negras juega con blancas en la revancha, y
     * viceversa. Se calculan al proponer (ver RematchController), no al aceptar, así
     * que aquí ya vienen resueltos.
     */
    public record PendingRematch(
            String fromUserId,
            String fromUsername,
            String toUserId,
            Color fromColorInRematch,
            Color toColorInRematch,
            TimeControl timeControl,
            String timeControlPreset
    ) {
    }

    private final Map<String, PendingRematch> pendingByTarget = new ConcurrentHashMap<>();

    public void propose(PendingRematch rematch) {
        pendingByTarget.put(rematch.toUserId(), rematch);
    }

    public Optional<PendingRematch> find(String targetUserId) {
        return Optional.ofNullable(pendingByTarget.get(targetUserId));
    }

    public void clear(String targetUserId) {
        pendingByTarget.remove(targetUserId);
    }
}
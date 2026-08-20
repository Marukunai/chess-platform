package com.chessplatform.challenge;

import com.chessplatform.matchmaking.TimeControl;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Retos directos a un amigo pendientes de responder — mismo patrón que
 * RematchService: solo un reto pendiente por destinatario a la vez, proponer uno
 * segundo sustituye al primero en vez de acumularlos. A diferencia de la revancha, aquí
 * no hay "partida anterior" de la que sacar los colores — se sortean al aceptar, igual
 * que en el emparejamiento normal (ver ChallengeController.respond()).
 */
@Component
public class ChallengeService {

    public record PendingChallenge(
            String fromUserId,
            String fromUsername,
            String toUserId,
            TimeControl timeControl,
            String timeControlPreset
    ) {
    }

    private final Map<String, PendingChallenge> pendingByTarget = new ConcurrentHashMap<>();

    public void propose(PendingChallenge challenge) {
        pendingByTarget.put(challenge.toUserId(), challenge);
    }

    public Optional<PendingChallenge> find(String targetUserId) {
        return Optional.ofNullable(pendingByTarget.get(targetUserId));
    }

    public void clear(String targetUserId) {
        pendingByTarget.remove(targetUserId);
    }
}
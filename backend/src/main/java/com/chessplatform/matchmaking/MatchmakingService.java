package com.chessplatform.matchmaking;

import com.chessplatform.matchmaking.dto.MatchFoundMessage;
import com.chessplatform.presence.PresenceService;
import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Empareja jugadores de la cola cada pocos segundos, ampliando la ventana de tolerancia
 * de rating cuanto más tiempo lleva alguien esperando (versión simplificada del seek
 * graph de Lichess).
 */
@Service
public class MatchmakingService {

    private static final long TICK_INTERVAL_MS = 2000;
    private static final int INITIAL_RATING_WINDOW = 100;
    private static final int WINDOW_GROWTH_PER_TICK = 50;

    private final MatchmakingQueue queue;
    private final GameSessionRegistry sessionRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;
    private final Random random = new Random();

    public MatchmakingService(MatchmakingQueue queue, GameSessionRegistry sessionRegistry,
                              SimpMessagingTemplate messagingTemplate, PresenceService presenceService) {
        this.queue = queue;
        this.sessionRegistry = sessionRegistry;
        this.messagingTemplate = messagingTemplate;
        this.presenceService = presenceService;
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    public void tick() {
        List<MatchmakingQueue.WaitingPlayer> waiting = queue.snapshot();
        if (waiting.size() < 2) {
            return;
        }

        // Prioridad a quien lleva más tiempo esperando: se le busca rival primero.
        List<MatchmakingQueue.WaitingPlayer> byLongestWaiting = new ArrayList<>(waiting);
        byLongestWaiting.sort(Comparator.comparing(MatchmakingQueue.WaitingPlayer::queuedAt));

        Set<String> matchedThisTick = new HashSet<>();
        List<MatchmakingQueue.WaitingPlayer> toRemoveFromQueue = new ArrayList<>();

        for (MatchmakingQueue.WaitingPlayer player : byLongestWaiting) {
            if (matchedThisTick.contains(player.playerId())) {
                continue; // ya emparejado como rival de alguien anterior en este mismo tick
            }

            findBestOpponent(player, byLongestWaiting, matchedThisTick).ifPresent(opponent -> {
                matchedThisTick.add(player.playerId());
                matchedThisTick.add(opponent.playerId());
                toRemoveFromQueue.add(player);
                toRemoveFromQueue.add(opponent);
                pairUp(player, opponent);
            });
        }

        queue.removeAll(toRemoveFromQueue);
    }

    /**
     * Busca, entre los candidatos válidos (mismo control de tiempo, no emparejado ya en
     * este tick, no es él mismo), el de rating más cercano dentro de la ventana de
     * tolerancia actual de `player`. Usamos el más cercano y no simplemente "el primero
     * que entre en la ventana" — con varios candidatos válidos, la pareja más pareja en
     * fuerza da una partida más interesante para ambos.
     */
    private Optional<MatchmakingQueue.WaitingPlayer> findBestOpponent(
            MatchmakingQueue.WaitingPlayer player,
            List<MatchmakingQueue.WaitingPlayer> candidates,
            Set<String> matchedThisTick) {

        int window = ratingWindowFor(player);

        return candidates.stream()
                .filter(candidate -> !candidate.playerId().equals(player.playerId()))
                .filter(candidate -> !matchedThisTick.contains(candidate.playerId()))
                .filter(candidate -> candidate.timeControl().equals(player.timeControl()))
                .filter(candidate -> Math.abs(candidate.rating() - player.rating()) <= window)
                .min(Comparator.comparingInt(candidate -> Math.abs(candidate.rating() - player.rating())));
    }

    private int ratingWindowFor(MatchmakingQueue.WaitingPlayer player) {
        long ticksWaited = player.waitingTime(queue.now()).toMillis() / TICK_INTERVAL_MS;
        return INITIAL_RATING_WINDOW + (int) (ticksWaited * WINDOW_GROWTH_PER_TICK);
    }

    private void pairUp(MatchmakingQueue.WaitingPlayer a, MatchmakingQueue.WaitingPlayer b) {
        boolean aIsWhite = random.nextBoolean();
        String whitePlayerId = aIsWhite ? a.playerId() : b.playerId();
        String blackPlayerId = aIsWhite ? b.playerId() : a.playerId();
        String whiteUsername = aIsWhite ? a.username() : b.username();
        String blackUsername = aIsWhite ? b.username() : a.username();
        String whiteAvatarUrl = aIsWhite ? a.avatarUrl() : b.avatarUrl();
        String blackAvatarUrl = aIsWhite ? b.avatarUrl() : a.avatarUrl();

        GameSession session = new GameSession(
                whitePlayerId, blackPlayerId, a.timeControl().initialTime(), a.timeControl().increment());
        session.setUsernames(whiteUsername, blackUsername);
        session.setAvatars(whiteAvatarUrl, blackAvatarUrl);
        sessionRegistry.create(session);

        messagingTemplate.convertAndSend(
                "/topic/matchmaking/%s".formatted(whitePlayerId),
                new MatchFoundMessage(session.gameId(), "white"));
        messagingTemplate.convertAndSend(
                "/topic/matchmaking/%s".formatted(blackPlayerId),
                new MatchFoundMessage(session.gameId(), "black"));

        // Sus amigos deben verlos pasar a "en partida" sin tener que esperar a que se
        // reconecten — statusOf() ya calcula esto solo a partir de GameSessionRegistry,
        // pero nadie avisa del cambio si no se lo pedimos explícitamente aquí.
        presenceService.notifyFriendsOfStatusChange(whitePlayerId);
        presenceService.notifyFriendsOfStatusChange(blackPlayerId);
    }
}
package com.chessplatform.presence;

import com.chessplatform.persistence.entity.Friendship;
import com.chessplatform.persistence.repository.FriendshipRepository;
import com.chessplatform.presence.dto.PresenceUpdateMessage;
import com.chessplatform.realtime.GameSessionRegistry;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Punto único para lo de presencia: calcular el estado de alguien y avisar a sus
 * amigos cuando cambia. Ni PresenceConnectionListener ni PresenceController calculan
 * nada por su cuenta — los dos pasan por aquí, así el criterio de "qué estado le
 * corresponde a quién" vive en un solo sitio.
 */
@Component
public class PresenceService {

    private final PresenceRegistry presenceRegistry;
    private final GameSessionRegistry gameSessionRegistry;
    private final FriendshipRepository friendshipRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public PresenceService(PresenceRegistry presenceRegistry, GameSessionRegistry gameSessionRegistry,
                           FriendshipRepository friendshipRepository, SimpMessagingTemplate messagingTemplate) {
        this.presenceRegistry = presenceRegistry;
        this.gameSessionRegistry = gameSessionRegistry;
        this.friendshipRepository = friendshipRepository;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * "no molestar" y "en partida" solo tienen sentido para alguien conectado, por eso
     * se comprueban en este orden: offline manda por encima de todo lo demás, y "no
     * molestar" (que el propio usuario elige) manda por encima de "en partida" (que se
     * calcula solo) — si alguien activó no molestar, respetarlo aunque esté jugando.
     */
    public String statusOf(String userId) {
        if (!presenceRegistry.isOnline(userId)) {
            return "OFFLINE";
        }
        if (presenceRegistry.isDoNotDisturb(userId)) {
            return "DO_NOT_DISTURB";
        }
        if (gameSessionRegistry.findByPlayerId(userId).isPresent()) {
            return "IN_GAME";
        }
        return "ONLINE";
    }

    public void markOnlineAndNotifyFriends(String userId) {
        presenceRegistry.markOnline(userId);
        notifyFriends(userId);
    }

    public void markOfflineAndNotifyFriends(String userId) {
        presenceRegistry.markOffline(userId);
        notifyFriends(userId);
    }

    public void setDoNotDisturbAndNotifyFriends(String userId, boolean enabled) {
        presenceRegistry.setDoNotDisturb(userId, enabled);
        notifyFriends(userId);
    }

    private void notifyFriends(String userId) {
        String status = statusOf(userId);
        for (Friendship friendship : friendshipRepository.findAcceptedFriendships(userId)) {
            String friendId = friendship.theOtherUser(userId).getId();
            messagingTemplate.convertAndSend(
                    "/topic/user/%s".formatted(friendId),
                    new PresenceUpdateMessage(userId, status));
        }
    }
}
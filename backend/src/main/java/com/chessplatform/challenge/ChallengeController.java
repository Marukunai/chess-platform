package com.chessplatform.challenge;

import com.chessplatform.challenge.dto.ChallengeDeclinedMessage;
import com.chessplatform.challenge.dto.ChallengeOfferMessage;
import com.chessplatform.challenge.dto.ChallengeProposalMessage;
import com.chessplatform.challenge.dto.ChallengeResponseMessage;
import com.chessplatform.matchmaking.TimeControl;
import com.chessplatform.matchmaking.dto.MatchFoundMessage;
import com.chessplatform.matchmaking.dto.MatchmakingJoinMessage;
import com.chessplatform.persistence.entity.Friendship;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.FriendshipRepository;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.presence.PresenceService;
import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
import com.chessplatform.realtime.dto.ErrorMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Optional;
import java.util.Random;

/**
 * Punto de entrada STOMP para retar directamente a un amigo, sin pasar por el
 * emparejamiento aleatorio — mismo patrón que RematchController (propuesta pendiente,
 * aviso por /topic/user/{userId}, aceptar crea la partida), pero sin partida anterior
 * de la que sacar los colores: se sortean al aceptar, igual que en
 * MatchmakingService.pairUp().
 */
@Controller
public class ChallengeController {

    private final ChallengeService challengeService;
    private final FriendshipRepository friendshipRepository;
    private final GameSessionRegistry sessionRegistry;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;
    private final Random random = new Random();

    public ChallengeController(ChallengeService challengeService, FriendshipRepository friendshipRepository,
                               GameSessionRegistry sessionRegistry, UserRepository userRepository,
                               SimpMessagingTemplate messagingTemplate, PresenceService presenceService) {
        this.challengeService = challengeService;
        this.friendshipRepository = friendshipRepository;
        this.sessionRegistry = sessionRegistry;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.presenceService = presenceService;
    }

    @MessageMapping("/challenge/propose")
    public void propose(ChallengeProposalMessage message, Principal principal) {
        if (principal == null) {
            return;
        }
        String fromUserId = principal.getName();

        if (!areFriends(fromUserId, message.opponentUserId())) {
            sendErrorToUser(fromUserId, "NOT_FRIENDS", "Solo puedes retar a tus amigos");
            return;
        }

        TimeControl timeControl;
        String preset;
        try {
            timeControl = new MatchmakingJoinMessage(message.timeControlPreset()).toTimeControl();
            preset = TimeControl.presetNameFor(timeControl.initialTime(), timeControl.increment()).orElseThrow();
        } catch (IllegalArgumentException e) {
            sendErrorToUser(fromUserId, "INVALID_TIME_CONTROL", e.getMessage());
            return;
        }

        String fromUsername = userRepository.findById(fromUserId).map(User::getUsername).orElse(fromUserId);

        challengeService.propose(new ChallengeService.PendingChallenge(
                fromUserId, fromUsername, message.opponentUserId(), timeControl, preset
        ));

        // El reto queda registrado igualmente aunque el destinatario tenga "no
        // molestar" — solo se silencia el aviso en vivo, igual que con la revancha.
        if (!presenceService.isDoNotDisturb(message.opponentUserId())) {
            messagingTemplate.convertAndSend(
                    "/topic/user/%s".formatted(message.opponentUserId()),
                    new ChallengeOfferMessage(fromUserId, fromUsername, preset, true)
            );
        }
    }

    @MessageMapping("/challenge/respond")
    public void respond(ChallengeResponseMessage message, Principal principal) {
        if (principal == null) {
            return;
        }
        String toUserId = principal.getName();

        Optional<ChallengeService.PendingChallenge> maybePending = challengeService.find(toUserId);
        if (maybePending.isEmpty()) {
            sendErrorToUser(toUserId, "NO_PENDING_CHALLENGE", "No hay ningún reto pendiente para responder");
            return;
        }
        ChallengeService.PendingChallenge pending = maybePending.get();
        challengeService.clear(toUserId);

        if (!message.accept()) {
            String toUsername = userRepository.findById(toUserId).map(User::getUsername).orElse(toUserId);
            if (!presenceService.isDoNotDisturb(pending.fromUserId())) {
                messagingTemplate.convertAndSend(
                        "/topic/user/%s".formatted(pending.fromUserId()),
                        new ChallengeDeclinedMessage(toUsername, true)
                );
            }
            return;
        }

        // Sin partida anterior de la que sacar los colores — se sortean, igual que en
        // el emparejamiento normal (ver MatchmakingService.pairUp()).
        boolean fromUserIsWhite = random.nextBoolean();
        String whitePlayerId = fromUserIsWhite ? pending.fromUserId() : toUserId;
        String blackPlayerId = fromUserIsWhite ? toUserId : pending.fromUserId();

        GameSession session = new GameSession(
                whitePlayerId, blackPlayerId, pending.timeControl().initialTime(), pending.timeControl().increment());
        String whiteUsername = whitePlayerId.equals(pending.fromUserId()) ? pending.fromUsername() : usernameOf(toUserId);
        String blackUsername = blackPlayerId.equals(pending.fromUserId()) ? pending.fromUsername() : usernameOf(toUserId);
        session.setUsernames(whiteUsername, blackUsername);
        session.setAvatars(avatarUrlOf(whitePlayerId), avatarUrlOf(blackPlayerId));
        sessionRegistry.create(session);

        messagingTemplate.convertAndSend(
                "/topic/user/%s".formatted(whitePlayerId),
                new MatchFoundMessage(session.gameId(), "white"));
        messagingTemplate.convertAndSend(
                "/topic/user/%s".formatted(blackPlayerId),
                new MatchFoundMessage(session.gameId(), "black"));

        presenceService.notifyFriendsOfStatusChange(whitePlayerId);
        presenceService.notifyFriendsOfStatusChange(blackPlayerId);
    }

    private boolean areFriends(String userId, String otherUserId) {
        Optional<Friendship> friendship = friendshipRepository.findBetween(userId, otherUserId);
        return friendship.isPresent() && Friendship.STATUS_ACCEPTED.equals(friendship.get().getStatus());
    }

    private String usernameOf(String userId) {
        return userRepository.findById(userId).map(User::getUsername).orElse(userId);
    }

    private String avatarUrlOf(String userId) {
        return userRepository.findById(userId).map(User::getAvatarUrl).orElse(null);
    }

    private void sendErrorToUser(String userId, String code, String message) {
        messagingTemplate.convertAndSend(
                "/topic/user/%s".formatted(userId),
                new ErrorMessage(code, message)
        );
    }
}
package com.chessplatform.rematch;

import com.chessplatform.engine.Color;
import com.chessplatform.matchmaking.TimeControl;
import com.chessplatform.matchmaking.dto.MatchFoundMessage;
import com.chessplatform.matchmaking.dto.MatchmakingJoinMessage;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
import com.chessplatform.realtime.dto.ErrorMessage;
import com.chessplatform.rematch.dto.RematchDeclinedMessage;
import com.chessplatform.rematch.dto.RematchOfferMessage;
import com.chessplatform.rematch.dto.RematchProposalMessage;
import com.chessplatform.rematch.dto.RematchResponseMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Optional;

/**
 * Punto de entrada STOMP para proponer/responder revanchas. A diferencia de una jugada o
 * una oferta de tablas, esto pasa DESPUÉS de que la partida original ya terminó y su
 * GameSession ya no existe — por eso el aviso al rival va por /topic/user/{userId} (ver
 * ADR correspondiente), un canal por-usuario al que el cliente se suscribe una sola vez
 * al conectar y mantiene mientras dure la sesión, sin importar en qué pantalla esté.
 */
@Controller
public class RematchController {

    private final RematchService rematchService;
    private final GameSessionRegistry sessionRegistry;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public RematchController(RematchService rematchService, GameSessionRegistry sessionRegistry,
                             UserRepository userRepository, SimpMessagingTemplate messagingTemplate) {
        this.rematchService = rematchService;
        this.sessionRegistry = sessionRegistry;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/rematch/propose")
    public void propose(RematchProposalMessage message, Principal principal) {
        if (principal == null) {
            return;
        }
        String fromUserId = principal.getName();

        TimeControl timeControl;
        String preset;
        try {
            timeControl = new MatchmakingJoinMessage(message.timeControlPreset()).toTimeControl();
            preset = TimeControl.presetNameFor(timeControl.initialTime(), timeControl.increment()).orElseThrow();
        } catch (IllegalArgumentException e) {
            sendErrorToUser(fromUserId, "INVALID_TIME_CONTROL", e.getMessage());
            return;
        }

        Color myPreviousColor = "white".equalsIgnoreCase(message.myColorInPreviousGame()) ? Color.WHITE : Color.BLACK;
        // Intercambiados a propósito respecto a la partida anterior — quien perdió con
        // negras juega con blancas en la revancha, y viceversa (ver javadoc de la clase).
        Color myColorInRematch = myPreviousColor.opposite();
        Color opponentColorInRematch = myPreviousColor;

        String fromUsername = userRepository.findById(fromUserId).map(User::getUsername).orElse(fromUserId);

        rematchService.propose(new RematchService.PendingRematch(
                fromUserId, fromUsername, message.opponentUserId(),
                myColorInRematch, opponentColorInRematch, timeControl, preset
        ));

        messagingTemplate.convertAndSend(
                "/topic/user/%s".formatted(message.opponentUserId()),
                new RematchOfferMessage(fromUserId, fromUsername, preset)
        );
    }

    @MessageMapping("/rematch/respond")
    public void respond(RematchResponseMessage message, Principal principal) {
        if (principal == null) {
            return;
        }
        String toUserId = principal.getName();

        Optional<RematchService.PendingRematch> maybePending = rematchService.find(toUserId);
        if (maybePending.isEmpty()) {
            sendErrorToUser(toUserId, "NO_PENDING_REMATCH", "No hay ninguna revancha pendiente para responder");
            return;
        }
        RematchService.PendingRematch pending = maybePending.get();
        rematchService.clear(toUserId);

        if (!message.accept()) {
            String toUsername = userRepository.findById(toUserId).map(User::getUsername).orElse(toUserId);
            messagingTemplate.convertAndSend(
                    "/topic/user/%s".formatted(pending.fromUserId()),
                    new RematchDeclinedMessage(toUsername)
            );
            return;
        }

        String whitePlayerId = pending.fromColorInRematch() == Color.WHITE ? pending.fromUserId() : toUserId;
        String blackPlayerId = pending.fromColorInRematch() == Color.WHITE ? toUserId : pending.fromUserId();

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
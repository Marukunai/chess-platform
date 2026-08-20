package com.chessplatform.rematch;

import com.chessplatform.matchmaking.dto.MatchFoundMessage;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.presence.PresenceService;
import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
import com.chessplatform.realtime.dto.ErrorMessage;
import com.chessplatform.rematch.dto.RematchDeclinedMessage;
import com.chessplatform.rematch.dto.RematchOfferMessage;
import com.chessplatform.rematch.dto.RematchProposalMessage;
import com.chessplatform.rematch.dto.RematchResponseMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RematchControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PresenceService presenceService;

    private RematchService rematchService;
    private GameSessionRegistry sessionRegistry;
    private RematchController controller;

    @BeforeEach
    void setUp() {
        rematchService = new RematchService();
        sessionRegistry = new GameSessionRegistry();
        controller = new RematchController(rematchService, sessionRegistry, userRepository, messagingTemplate, presenceService);
    }

    private static Principal principalFor(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void proposeSendsTheOfferToTheOpponentsUserTopic() {
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(new User("alice", "hash")));

        controller.propose(new RematchProposalMessage("bob-id", "BLITZ", "black"), principalFor("alice-id"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob-id"), payload.capture());
        RematchOfferMessage offer = (RematchOfferMessage) payload.getValue();
        assertThat(offer.fromUserId()).isEqualTo("alice-id");
        assertThat(offer.fromUsername()).isEqualTo("alice");
        assertThat(offer.timeControlPreset()).isEqualTo("BLITZ");
    }

    @Test
    void proposeSwapsColorsRelativeToThePreviousGame() {
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(new User("alice", "hash")));

        // Alice jugó con negras la partida anterior -> en la revancha le tocan blancas.
        controller.propose(new RematchProposalMessage("bob-id", "BLITZ", "black"), principalFor("alice-id"));

        RematchService.PendingRematch pending = rematchService.find("bob-id").orElseThrow();
        assertThat(pending.fromColorInRematch()).isEqualTo(com.chessplatform.engine.Color.WHITE);
        assertThat(pending.toColorInRematch()).isEqualTo(com.chessplatform.engine.Color.BLACK);
    }

    @Test
    void proposeSendsErrorForAnUnknownTimeControl() {
        controller.propose(new RematchProposalMessage("bob-id", "ULTRA-INVENTADO", "white"), principalFor("alice-id"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/alice-id"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(ErrorMessage.class);
        assertThat(rematchService.find("bob-id")).isEmpty();
    }

    @Test
    void respondAcceptingCreatesANewSessionWithColorsAlreadySwapped() {
        when(userRepository.findById("bob-id")).thenReturn(Optional.of(new User("bob", "hash")));
        rematchService.propose(new RematchService.PendingRematch(
                "alice-id", "alice", "bob-id",
                com.chessplatform.engine.Color.WHITE, com.chessplatform.engine.Color.BLACK,
                com.chessplatform.matchmaking.TimeControl.BLITZ, "BLITZ"));

        controller.respond(new RematchResponseMessage(true), principalFor("bob-id"));

        assertThat(sessionRegistry.activeCount()).isEqualTo(1);
        GameSession created = sessionRegistry.allSessions().iterator().next();
        assertThat(created.whitePlayerId()).isEqualTo("alice-id");
        assertThat(created.blackPlayerId()).isEqualTo("bob-id");
        assertThat(created.whiteUsername()).isEqualTo("alice");
        assertThat(created.blackUsername()).isEqualTo("bob");
    }

    @Test
    void respondAcceptingNotifiesBothPlayersViaTheirUserTopic() {
        when(userRepository.findById("bob-id")).thenReturn(Optional.of(new User("bob", "hash")));
        rematchService.propose(new RematchService.PendingRematch(
                "alice-id", "alice", "bob-id",
                com.chessplatform.engine.Color.WHITE, com.chessplatform.engine.Color.BLACK,
                com.chessplatform.matchmaking.TimeControl.BLITZ, "BLITZ"));

        controller.respond(new RematchResponseMessage(true), principalFor("bob-id"));

        ArgumentCaptor<Object> whitePayload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/alice-id"), whitePayload.capture());
        assertThat(((MatchFoundMessage) whitePayload.getValue()).color()).isEqualTo("white");

        ArgumentCaptor<Object> blackPayload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob-id"), blackPayload.capture());
        assertThat(((MatchFoundMessage) blackPayload.getValue()).color()).isEqualTo("black");
    }

    @Test
    void respondDecliningNotifiesTheProposerAndClearsTheOffer() {
        when(userRepository.findById("bob-id")).thenReturn(Optional.of(new User("bob", "hash")));
        rematchService.propose(new RematchService.PendingRematch(
                "alice-id", "alice", "bob-id",
                com.chessplatform.engine.Color.WHITE, com.chessplatform.engine.Color.BLACK,
                com.chessplatform.matchmaking.TimeControl.BLITZ, "BLITZ"));

        controller.respond(new RematchResponseMessage(false), principalFor("bob-id"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/alice-id"), payload.capture());
        assertThat(((RematchDeclinedMessage) payload.getValue()).byUsername()).isEqualTo("bob");
        assertThat(rematchService.find("bob-id")).isEmpty();
        assertThat(sessionRegistry.activeCount()).isZero();
    }

    @Test
    void respondSendsErrorWhenThereIsNoPendingOffer() {
        controller.respond(new RematchResponseMessage(true), principalFor("bob-id"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob-id"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(ErrorMessage.class);
    }

    @Test
    void proposeSuppressesTheOfferWhenTheTargetHasDoNotDisturbEnabled() {
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(new User("alice", "hash")));
        when(presenceService.isDoNotDisturb("bob-id")).thenReturn(true);

        controller.propose(new RematchProposalMessage("bob-id", "BLITZ", "black"), principalFor("alice-id"));

        // La propuesta se registra igualmente (rematchService la tiene) — solo se
        // silencia el aviso en vivo, no la propuesta en sí.
        assertThat(rematchService.find("bob-id")).isPresent();
        verify(messagingTemplate, org.mockito.Mockito.never())
                .convertAndSend(eq("/topic/user/bob-id"), org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void respondDecliningSuppressesTheNotificationWhenTheProposerHasDoNotDisturbEnabled() {
        when(userRepository.findById("bob-id")).thenReturn(Optional.of(new User("bob", "hash")));
        when(presenceService.isDoNotDisturb("alice-id")).thenReturn(true);
        rematchService.propose(new RematchService.PendingRematch(
                "alice-id", "alice", "bob-id",
                com.chessplatform.engine.Color.WHITE, com.chessplatform.engine.Color.BLACK,
                com.chessplatform.matchmaking.TimeControl.BLITZ, "BLITZ"));

        controller.respond(new RematchResponseMessage(false), principalFor("bob-id"));

        verify(messagingTemplate, org.mockito.Mockito.never())
                .convertAndSend(eq("/topic/user/alice-id"), org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void respondAcceptingNotifiesFriendsOfBothPlayersThatTheyAreNowInAGame() {
        when(userRepository.findById("bob-id")).thenReturn(Optional.of(new User("bob", "hash")));
        rematchService.propose(new RematchService.PendingRematch(
                "alice-id", "alice", "bob-id",
                com.chessplatform.engine.Color.WHITE, com.chessplatform.engine.Color.BLACK,
                com.chessplatform.matchmaking.TimeControl.BLITZ, "BLITZ"));

        controller.respond(new RematchResponseMessage(true), principalFor("bob-id"));

        verify(presenceService).notifyFriendsOfStatusChange("alice-id");
        verify(presenceService).notifyFriendsOfStatusChange("bob-id");
    }
}
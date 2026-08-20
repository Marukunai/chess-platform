package com.chessplatform.challenge;

import com.chessplatform.challenge.dto.ChallengeDeclinedMessage;
import com.chessplatform.challenge.dto.ChallengeOfferMessage;
import com.chessplatform.challenge.dto.ChallengeProposalMessage;
import com.chessplatform.challenge.dto.ChallengeResponseMessage;
import com.chessplatform.matchmaking.TimeControl;
import com.chessplatform.matchmaking.dto.MatchFoundMessage;
import com.chessplatform.persistence.entity.Friendship;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.FriendshipRepository;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.presence.PresenceService;
import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
import com.chessplatform.realtime.dto.ErrorMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeControllerTest {

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PresenceService presenceService;

    private ChallengeService challengeService;
    private GameSessionRegistry sessionRegistry;
    private ChallengeController controller;

    @BeforeEach
    void setUp() {
        challengeService = new ChallengeService();
        sessionRegistry = new GameSessionRegistry();
        controller = new ChallengeController(challengeService, friendshipRepository, sessionRegistry,
                userRepository, messagingTemplate, presenceService);
    }

    private static Principal principalFor(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private static void setId(User user, String id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void proposeRejectsSomeoneWhoIsNotYourFriend() {
        when(friendshipRepository.findBetween("alice-id", "un-extrano-id")).thenReturn(Optional.empty());

        controller.propose(new ChallengeProposalMessage("un-extrano-id", "BLITZ"), principalFor("alice-id"));

        assertThat(challengeService.find("un-extrano-id")).isEmpty();
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/alice-id"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(ErrorMessage.class);
    }

    @Test
    void proposeRejectsAPendingNotYetAcceptedFriendship() {
        User alice = new User("alice", "hash");
        User bob = new User("bob", "hash");
        when(friendshipRepository.findBetween("alice-id", "bob-id"))
                .thenReturn(Optional.of(new Friendship(alice, bob))); // sin accept()

        controller.propose(new ChallengeProposalMessage("bob-id", "BLITZ"), principalFor("alice-id"));

        assertThat(challengeService.find("bob-id")).isEmpty();
    }

    @Test
    void proposeSendsTheOfferToTheOpponentsUserTopic() {
        User alice = new User("alice", "hash");
        User bob = new User("bob", "hash");
        Friendship friendship = new Friendship(alice, bob);
        friendship.accept();
        when(friendshipRepository.findBetween("alice-id", "bob-id")).thenReturn(Optional.of(friendship));
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));

        controller.propose(new ChallengeProposalMessage("bob-id", "BLITZ"), principalFor("alice-id"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob-id"), payload.capture());
        ChallengeOfferMessage offer = (ChallengeOfferMessage) payload.getValue();
        assertThat(offer.fromUserId()).isEqualTo("alice-id");
        assertThat(offer.fromUsername()).isEqualTo("alice");
        assertThat(offer.timeControlPreset()).isEqualTo("BLITZ");
        assertThat(offer.challenge()).isTrue();
    }

    @Test
    void proposeSendsErrorForAnUnknownTimeControl() {
        User alice = new User("alice", "hash");
        User bob = new User("bob", "hash");
        Friendship friendship = new Friendship(alice, bob);
        friendship.accept();
        when(friendshipRepository.findBetween("alice-id", "bob-id")).thenReturn(Optional.of(friendship));

        controller.propose(new ChallengeProposalMessage("bob-id", "ULTRA-INVENTADO"), principalFor("alice-id"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/alice-id"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(ErrorMessage.class);
        assertThat(challengeService.find("bob-id")).isEmpty();
    }

    @Test
    void proposeSuppressesTheOfferWhenTheTargetHasDoNotDisturbEnabled() {
        User alice = new User("alice", "hash");
        User bob = new User("bob", "hash");
        Friendship friendship = new Friendship(alice, bob);
        friendship.accept();
        when(friendshipRepository.findBetween("alice-id", "bob-id")).thenReturn(Optional.of(friendship));
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));
        when(presenceService.isDoNotDisturb("bob-id")).thenReturn(true);

        controller.propose(new ChallengeProposalMessage("bob-id", "BLITZ"), principalFor("alice-id"));

        // El reto se registra igualmente — solo se silencia el aviso en vivo.
        assertThat(challengeService.find("bob-id")).isPresent();
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/user/bob-id"), any(Object.class));
    }

    @Test
    void respondSendsErrorWhenThereIsNoPendingChallenge() {
        controller.respond(new ChallengeResponseMessage(true), principalFor("bob-id"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob-id"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(ErrorMessage.class);
    }

    @Test
    void respondDecliningNotifiesTheProposerAndClearsTheChallenge() {
        when(userRepository.findById("bob-id")).thenReturn(Optional.of(new User("bob", "hash")));
        challengeService.propose(new ChallengeService.PendingChallenge(
                "alice-id", "alice", "bob-id", TimeControl.BLITZ, "BLITZ"));

        controller.respond(new ChallengeResponseMessage(false), principalFor("bob-id"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/alice-id"), payload.capture());
        ChallengeDeclinedMessage declined = (ChallengeDeclinedMessage) payload.getValue();
        assertThat(declined.byUsername()).isEqualTo("bob");
        assertThat(declined.challenge()).isTrue();
        assertThat(challengeService.find("bob-id")).isEmpty();
        assertThat(sessionRegistry.activeCount()).isZero();
    }

    @Test
    void respondDecliningSuppressesTheNotificationWhenTheProposerHasDoNotDisturbEnabled() {
        when(userRepository.findById("bob-id")).thenReturn(Optional.of(new User("bob", "hash")));
        when(presenceService.isDoNotDisturb("alice-id")).thenReturn(true);
        challengeService.propose(new ChallengeService.PendingChallenge(
                "alice-id", "alice", "bob-id", TimeControl.BLITZ, "BLITZ"));

        controller.respond(new ChallengeResponseMessage(false), principalFor("bob-id"));

        verify(messagingTemplate, never()).convertAndSend(eq("/topic/user/alice-id"), any(Object.class));
    }

    @Test
    void respondAcceptingCreatesANewSessionWithBothPlayers() {
        when(userRepository.findById("bob-id")).thenReturn(Optional.of(new User("bob", "hash")));
        challengeService.propose(new ChallengeService.PendingChallenge(
                "alice-id", "alice", "bob-id", TimeControl.BLITZ, "BLITZ"));

        controller.respond(new ChallengeResponseMessage(true), principalFor("bob-id"));

        assertThat(sessionRegistry.activeCount()).isEqualTo(1);
        GameSession created = sessionRegistry.allSessions().iterator().next();
        assertThat(List.of(created.whitePlayerId(), created.blackPlayerId()))
                .containsExactlyInAnyOrder("alice-id", "bob-id");
        assertThat(List.of(created.whiteUsername(), created.blackUsername()))
                .containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void respondAcceptingNotifiesBothPlayersViaTheirUserTopic() {
        when(userRepository.findById("bob-id")).thenReturn(Optional.of(new User("bob", "hash")));
        challengeService.propose(new ChallengeService.PendingChallenge(
                "alice-id", "alice", "bob-id", TimeControl.BLITZ, "BLITZ"));

        controller.respond(new ChallengeResponseMessage(true), principalFor("bob-id"));

        verify(messagingTemplate).convertAndSend(eq("/topic/user/alice-id"), any(MatchFoundMessage.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob-id"), any(MatchFoundMessage.class));
    }

    @Test
    void respondAcceptingNotifiesFriendsOfBothPlayersThatTheyAreNowInAGame() {
        when(userRepository.findById("bob-id")).thenReturn(Optional.of(new User("bob", "hash")));
        challengeService.propose(new ChallengeService.PendingChallenge(
                "alice-id", "alice", "bob-id", TimeControl.BLITZ, "BLITZ"));

        controller.respond(new ChallengeResponseMessage(true), principalFor("bob-id"));

        verify(presenceService).notifyFriendsOfStatusChange("alice-id");
        verify(presenceService).notifyFriendsOfStatusChange("bob-id");
    }
}
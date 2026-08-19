package com.chessplatform.presence;

import com.chessplatform.persistence.entity.Friendship;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.FriendshipRepository;
import com.chessplatform.presence.dto.PresenceUpdateMessage;
import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock
    private GameSessionRegistry gameSessionRegistry;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private PresenceRegistry presenceRegistry;
    private PresenceService service;

    @BeforeEach
    void setUp() {
        presenceRegistry = new PresenceRegistry(); // real, no mockeado — es estado simple en memoria
        service = new PresenceService(presenceRegistry, gameSessionRegistry, friendshipRepository, messagingTemplate);
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
    void statusOfIsOfflineForSomeoneNotConnected() {
        assertThat(service.statusOf("alice-id")).isEqualTo("OFFLINE");
    }

    @Test
    void statusOfIsOnlineForSomeoneConnectedAndNotInAGame() {
        presenceRegistry.markOnline("alice-id");
        when(gameSessionRegistry.findByPlayerId("alice-id")).thenReturn(Optional.empty());

        assertThat(service.statusOf("alice-id")).isEqualTo("ONLINE");
    }

    @Test
    void statusOfIsInGameForSomeoneConnectedAndPlaying() {
        presenceRegistry.markOnline("alice-id");
        GameSession session = new GameSession("alice-id", "bob-id", Duration.ofMinutes(5), Duration.ZERO);
        when(gameSessionRegistry.findByPlayerId("alice-id")).thenReturn(Optional.of(session));

        assertThat(service.statusOf("alice-id")).isEqualTo("IN_GAME");
    }

    @Test
    void statusOfIsDoNotDisturbEvenWhilePlayingIfEnabled() {
        presenceRegistry.markOnline("alice-id");
        presenceRegistry.setDoNotDisturb("alice-id", true);
        // Ni falta que hace comprobar si está en partida — "no molestar" manda por
        // encima, así que gameSessionRegistry no debería ni consultarse.

        assertThat(service.statusOf("alice-id")).isEqualTo("DO_NOT_DISTURB");
        verify(gameSessionRegistry, never()).findByPlayerId("alice-id");
    }

    @Test
    void markOnlineAndNotifyFriendsSendsTheNewStatusToEachAcceptedFriend() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        Friendship friendship = new Friendship(alice, bob);
        friendship.accept();
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of(friendship));
        when(gameSessionRegistry.findByPlayerId("alice-id")).thenReturn(Optional.empty());

        service.markOnlineAndNotifyFriends("alice-id");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob-id"), payload.capture());
        PresenceUpdateMessage update = (PresenceUpdateMessage) payload.getValue();
        assertThat(update.userId()).isEqualTo("alice-id");
        assertThat(update.status()).isEqualTo("ONLINE");
    }

    @Test
    void markOfflineAndNotifyFriendsSendsOfflineToEachFriend() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        Friendship friendship = new Friendship(alice, bob);
        friendship.accept();
        presenceRegistry.markOnline("alice-id");
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of(friendship));

        service.markOfflineAndNotifyFriends("alice-id");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob-id"), payload.capture());
        assertThat(((PresenceUpdateMessage) payload.getValue()).status()).isEqualTo("OFFLINE");
    }

    @Test
    void notifyingFriendsWorksRegardlessOfWhichSideOfTheFriendshipTheUserIs() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        Friendship bobRequestedAlice = new Friendship(bob, alice); // alice es la addressee esta vez
        bobRequestedAlice.accept();
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of(bobRequestedAlice));
        when(gameSessionRegistry.findByPlayerId("alice-id")).thenReturn(Optional.empty());

        service.markOnlineAndNotifyFriends("alice-id");

        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob-id"), org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void setDoNotDisturbAndNotifyFriendsUpdatesTheFlagAndBroadcasts() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        Friendship friendship = new Friendship(alice, bob);
        friendship.accept();
        presenceRegistry.markOnline("alice-id");
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of(friendship));

        service.setDoNotDisturbAndNotifyFriends("alice-id", true);

        assertThat(presenceRegistry.isDoNotDisturb("alice-id")).isTrue();
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob-id"), payload.capture());
        assertThat(((PresenceUpdateMessage) payload.getValue()).status()).isEqualTo("DO_NOT_DISTURB");
    }
}
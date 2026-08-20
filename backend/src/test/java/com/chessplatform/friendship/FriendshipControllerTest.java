package com.chessplatform.friendship;

import com.chessplatform.friendship.dto.FriendRequestAcceptedNotification;
import com.chessplatform.friendship.dto.FriendRequestNotification;
import com.chessplatform.friendship.dto.FriendRequestResponse;
import com.chessplatform.friendship.dto.FriendResponse;
import com.chessplatform.friendship.dto.RespondFriendRequestRequest;
import com.chessplatform.friendship.dto.UserSearchResultResponse;
import com.chessplatform.persistence.entity.Friendship;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.FriendshipRepository;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.presence.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private PresenceService presenceService;

    private FriendshipController controller;

    @BeforeEach
    void setUp() {
        controller = new FriendshipController(userRepository, friendshipRepository, messagingTemplate, presenceService);
    }

    private static Authentication authenticationFor(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    /** User.getId() no tiene setter a propósito (lo gestiona JPA) — reflexión, igual que en otros tests del proyecto. */
    private static void setId(User user, String id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setFriendshipId(Friendship friendship, String id) {
        try {
            Field field = Friendship.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(friendship, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void searchReturnsNothingForAQueryShorterThanTwoCharacters() {
        List<UserSearchResultResponse> results = controller.search("a", authenticationFor("alice-id"));

        assertThat(results).isEmpty();
    }

    @Test
    void searchExcludesTheViewerFromTheirOwnResults() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        when(userRepository.findTop20ByUsernameContainingIgnoreCaseAndDeletedAtIsNull("ali"))
                .thenReturn(List.of(alice));

        List<UserSearchResultResponse> results = controller.search("ali", authenticationFor("alice-id"));

        assertThat(results).isEmpty();
    }

    @Test
    void searchReportsNoneWhenThereIsNoRelationshipYet() {
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        when(userRepository.findTop20ByUsernameContainingIgnoreCaseAndDeletedAtIsNull("bob")).thenReturn(List.of(bob));
        when(friendshipRepository.findBetween("alice-id", "bob-id")).thenReturn(Optional.empty());

        List<UserSearchResultResponse> results = controller.search("bob", authenticationFor("alice-id"));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().friendshipStatus()).isEqualTo("NONE");
    }

    @Test
    void searchReportsPendingSentWhenTheViewerAlreadySentTheRequest() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        when(userRepository.findTop20ByUsernameContainingIgnoreCaseAndDeletedAtIsNull("bob")).thenReturn(List.of(bob));
        when(friendshipRepository.findBetween("alice-id", "bob-id"))
                .thenReturn(Optional.of(new Friendship(alice, bob))); // alice fue quien la propuso

        List<UserSearchResultResponse> results = controller.search("bob", authenticationFor("alice-id"));

        assertThat(results.getFirst().friendshipStatus()).isEqualTo("PENDING_SENT");
    }

    @Test
    void searchReportsPendingReceivedWhenTheOtherPersonSentIt() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        when(userRepository.findTop20ByUsernameContainingIgnoreCaseAndDeletedAtIsNull("bob")).thenReturn(List.of(bob));
        when(friendshipRepository.findBetween("alice-id", "bob-id"))
                .thenReturn(Optional.of(new Friendship(bob, alice))); // bob fue quien la propuso

        List<UserSearchResultResponse> results = controller.search("bob", authenticationFor("alice-id"));

        assertThat(results.getFirst().friendshipStatus()).isEqualTo("PENDING_RECEIVED");
    }

    @Test
    void searchReportsFriendsWhenTheRequestWasAlreadyAccepted() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        Friendship accepted = new Friendship(alice, bob);
        accepted.accept();
        when(userRepository.findTop20ByUsernameContainingIgnoreCaseAndDeletedAtIsNull("bob")).thenReturn(List.of(bob));
        when(friendshipRepository.findBetween("alice-id", "bob-id")).thenReturn(Optional.of(accepted));

        List<UserSearchResultResponse> results = controller.search("bob", authenticationFor("alice-id"));

        assertThat(results.getFirst().friendshipStatus()).isEqualTo("FRIENDS");
    }

    @Test
    void sendRequestRejectsSendingToYourself() {
        assertThatThrownBy(() -> controller.sendRequest("alice-id", authenticationFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void sendRequestRejectsWhenARelationshipAlreadyExists() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));
        when(userRepository.findById("bob-id")).thenReturn(Optional.of(bob));
        when(friendshipRepository.findBetween("alice-id", "bob-id"))
                .thenReturn(Optional.of(new Friendship(alice, bob)));

        assertThatThrownBy(() -> controller.sendRequest("bob-id", authenticationFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class);
        verify(friendshipRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void sendRequestSavesItAndNotifiesTheTarget() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));
        when(userRepository.findById("bob-id")).thenReturn(Optional.of(bob));
        when(friendshipRepository.findBetween("alice-id", "bob-id")).thenReturn(Optional.empty());

        controller.sendRequest("bob-id", authenticationFor("alice-id"));

        ArgumentCaptor<Friendship> saved = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository).save(saved.capture());
        assertThat(saved.getValue().getRequester()).isSameAs(alice);
        assertThat(saved.getValue().getAddressee()).isSameAs(bob);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob-id"), payload.capture());
        FriendRequestNotification notification = (FriendRequestNotification) payload.getValue();
        assertThat(notification.fromUserId()).isEqualTo("alice-id");
        assertThat(notification.fromUsername()).isEqualTo("alice");
    }

    @Test
    void pendingRequestsOnlyReturnsOnesAddressedToTheViewer() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        Friendship incoming = new Friendship(alice, bob);
        setFriendshipId(incoming, "friendship-1");
        when(friendshipRepository.findByAddressee_IdAndStatus("bob-id", Friendship.STATUS_PENDING))
                .thenReturn(List.of(incoming));

        List<FriendRequestResponse> requests = controller.pendingRequests(authenticationFor("bob-id"));

        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().friendshipId()).isEqualTo("friendship-1");
        assertThat(requests.getFirst().fromUsername()).isEqualTo("alice");
    }

    @Test
    void respondToRequestRejectsSomeoneWhoIsNotTheAddressee() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        Friendship friendship = new Friendship(alice, bob);
        when(friendshipRepository.findById("friendship-1")).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() ->
                controller.respondToRequest("friendship-1", new RespondFriendRequestRequest(true),
                        authenticationFor("someone-else-id")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void respondToRequestAcceptingUpdatesStatusAndNotifiesTheRequester() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        Friendship friendship = new Friendship(alice, bob);
        when(friendshipRepository.findById("friendship-1")).thenReturn(Optional.of(friendship));

        controller.respondToRequest("friendship-1", new RespondFriendRequestRequest(true), authenticationFor("bob-id"));

        assertThat(friendship.getStatus()).isEqualTo(Friendship.STATUS_ACCEPTED);
        verify(friendshipRepository).save(friendship);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/alice-id"), payload.capture());
        assertThat(((FriendRequestAcceptedNotification) payload.getValue()).byUsername()).isEqualTo("bob");
    }

    @Test
    void respondToRequestDecliningDeletesTheRowInsteadOfSaving() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        Friendship friendship = new Friendship(alice, bob);
        when(friendshipRepository.findById("friendship-1")).thenReturn(Optional.of(friendship));

        controller.respondToRequest("friendship-1", new RespondFriendRequestRequest(false), authenticationFor("bob-id"));

        verify(friendshipRepository).delete(friendship);
        verify(friendshipRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(messagingTemplate, never()).convertAndSend(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void respondToRequestRejectsRespondingTwice() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        Friendship friendship = new Friendship(alice, bob);
        friendship.accept(); // ya respondida antes
        when(friendshipRepository.findById("friendship-1")).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() ->
                controller.respondToRequest("friendship-1", new RespondFriendRequestRequest(true), authenticationFor("bob-id")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void friendsListsBothDirectionsCorrectly() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        User carol = new User("carol", "hash");
        setId(carol, "carol-id");

        Friendship aliceRequestedBob = new Friendship(alice, bob);
        aliceRequestedBob.accept();
        Friendship carolRequestedAlice = new Friendship(carol, alice);
        carolRequestedAlice.accept();

        when(friendshipRepository.findAcceptedFriendships("alice-id"))
                .thenReturn(List.of(aliceRequestedBob, carolRequestedAlice));
        when(presenceService.statusOf(org.mockito.ArgumentMatchers.anyString())).thenReturn("OFFLINE");

        List<FriendResponse> friends = controller.friends(authenticationFor("alice-id"));

        assertThat(friends).extracting(FriendResponse::username).containsExactlyInAnyOrder("bob", "carol");
    }

    @Test
    void friendsIncludesEachFriendsCurrentPresenceStatus() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");

        Friendship friendship = new Friendship(alice, bob);
        friendship.accept();
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of(friendship));
        when(presenceService.statusOf("bob-id")).thenReturn("IN_GAME");

        List<FriendResponse> friends = controller.friends(authenticationFor("alice-id"));

        assertThat(friends).hasSize(1);
        assertThat(friends.getFirst().status()).isEqualTo("IN_GAME");
    }

    @Test
    void sendRequestSuppressesTheNotificationWhenTheTargetHasDoNotDisturbEnabled() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));
        when(userRepository.findById("bob-id")).thenReturn(Optional.of(bob));
        when(friendshipRepository.findBetween("alice-id", "bob-id")).thenReturn(Optional.empty());
        when(presenceService.isDoNotDisturb("bob-id")).thenReturn(true);

        controller.sendRequest("bob-id", authenticationFor("alice-id"));

        // La solicitud se guarda igualmente — solo se silencia el aviso en vivo.
        verify(friendshipRepository).save(org.mockito.ArgumentMatchers.any());
        verify(messagingTemplate, org.mockito.Mockito.never())
                .convertAndSend(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void respondToRequestAcceptingSuppressesTheNotificationWhenTheRequesterHasDoNotDisturbEnabled() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        Friendship friendship = new Friendship(alice, bob);
        when(friendshipRepository.findById("friendship-1")).thenReturn(Optional.of(friendship));
        when(presenceService.isDoNotDisturb("alice-id")).thenReturn(true);

        controller.respondToRequest("friendship-1", new RespondFriendRequestRequest(true), authenticationFor("bob-id"));

        // La amistad se acepta igualmente — solo se silencia el aviso en vivo.
        assertThat(friendship.getStatus()).isEqualTo(Friendship.STATUS_ACCEPTED);
        verify(messagingTemplate, org.mockito.Mockito.never())
                .convertAndSend(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Object.class));
    }
}
package com.chessplatform.friendship;

import com.chessplatform.friendship.dto.DirectMessageNotification;
import com.chessplatform.friendship.dto.DirectMessageResponse;
import com.chessplatform.friendship.dto.SendDirectMessageRequest;
import com.chessplatform.persistence.entity.DirectMessage;
import com.chessplatform.persistence.entity.Friendship;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.DirectMessageRepository;
import com.chessplatform.persistence.repository.FriendshipRepository;
import com.chessplatform.persistence.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectMessageControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private DirectMessageRepository directMessageRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private DirectMessageController controller;

    @BeforeEach
    void setUp() {
        controller = new DirectMessageController(userRepository, friendshipRepository, directMessageRepository, messagingTemplate);
    }

    private static Authentication authenticationFor(String userId) {
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
    void conversationRejectsSomeoneWhoIsNotYourFriend() {
        when(friendshipRepository.findBetween("alice-id", "bob-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.conversation("bob-id", authenticationFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void conversationRejectsAPendingNotYetAcceptedFriendship() {
        User alice = new User("alice", "hash");
        User bob = new User("bob", "hash");
        when(friendshipRepository.findBetween("alice-id", "bob-id"))
                .thenReturn(Optional.of(new Friendship(alice, bob))); // sin accept()

        assertThatThrownBy(() -> controller.conversation("bob-id", authenticationFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void conversationReturnsTheStoredMessagesInOrder() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        Friendship friendship = new Friendship(alice, bob);
        friendship.accept();
        when(friendshipRepository.findBetween("alice-id", "bob-id")).thenReturn(Optional.of(friendship));
        when(directMessageRepository.findConversation("alice-id", "bob-id"))
                .thenReturn(List.of(new DirectMessage(alice, bob, "hola"), new DirectMessage(bob, alice, "qué tal")));

        List<DirectMessageResponse> conversation = controller.conversation("bob-id", authenticationFor("alice-id"));

        assertThat(conversation).hasSize(2);
        assertThat(conversation.get(0).text()).isEqualTo("hola");
        assertThat(conversation.get(0).senderUserId()).isEqualTo("alice-id");
        assertThat(conversation.get(1).senderUserId()).isEqualTo("bob-id");
    }

    @Test
    void sendRejectsAnEmptyMessage() {
        User alice = new User("alice", "hash");
        User bob = new User("bob", "hash");
        Friendship friendship = new Friendship(alice, bob);
        friendship.accept();
        when(friendshipRepository.findBetween("alice-id", "bob-id")).thenReturn(Optional.of(friendship));

        assertThatThrownBy(() -> controller.send("bob-id", new SendDirectMessageRequest("   "), authenticationFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class);
        verify(directMessageRepository, never()).save(any());
    }

    @Test
    void sendTruncatesMessagesLongerThanTheLimit() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        Friendship friendship = new Friendship(alice, bob);
        friendship.accept();
        when(friendshipRepository.findBetween("alice-id", "bob-id")).thenReturn(Optional.of(friendship));
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));
        when(userRepository.findById("bob-id")).thenReturn(Optional.of(bob));
        // Mismo patrón que en el resto del proyecto para simular un save() de JPA: el
        // mock, sin esto, devuelve null por defecto (no es una entidad de verdad
        // guardándose contra una base de datos) — el controlador usa lo que save()
        // devuelve para leer el id y la fecha, así que sin este stub explota con NPE.
        when(directMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        String tooLong = "x".repeat(1200);

        DirectMessageResponse response = controller.send("bob-id", new SendDirectMessageRequest(tooLong), authenticationFor("alice-id"));

        assertThat(response.text()).hasSize(1000);
    }

    @Test
    void sendSavesTheMessageAndNotifiesTheRecipientsChannel() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        Friendship friendship = new Friendship(alice, bob);
        friendship.accept();
        when(friendshipRepository.findBetween("alice-id", "bob-id")).thenReturn(Optional.of(friendship));
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));
        when(userRepository.findById("bob-id")).thenReturn(Optional.of(bob));
        when(directMessageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        controller.send("bob-id", new SendDirectMessageRequest("hola bob"), authenticationFor("alice-id"));

        ArgumentCaptor<DirectMessage> saved = ArgumentCaptor.forClass(DirectMessage.class);
        verify(directMessageRepository).save(saved.capture());
        assertThat(saved.getValue().getText()).isEqualTo("hola bob");
        assertThat(saved.getValue().getSender()).isSameAs(alice);
        assertThat(saved.getValue().getRecipient()).isSameAs(bob);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/bob-id"), payload.capture());
        DirectMessageNotification notification = (DirectMessageNotification) payload.getValue();
        assertThat(notification.fromUserId()).isEqualTo("alice-id");
        assertThat(notification.fromUsername()).isEqualTo("alice");
        assertThat(notification.text()).isEqualTo("hola bob");
    }

    @Test
    void sendRejectsSomeoneWhoIsNotYourFriend() {
        when(friendshipRepository.findBetween("alice-id", "un-extrano-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                controller.send("un-extrano-id", new SendDirectMessageRequest("hola"), authenticationFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class);
        verify(directMessageRepository, never()).save(any());
    }
}
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
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Mensajes privados entre amigos — a diferencia del chat de partida (efímero, solo
 * retransmisión), esto se guarda de verdad, por eso vive aparte en su propio
 * controlador en vez de reutilizar GameWebSocketController. Bajo /api/friends, como el
 * resto de lo de amistad — necesita identidad igual que todo lo demás ahí.
 */
@RestController
@RequestMapping("/api/friends/{friendId}/messages")
public class DirectMessageController {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final DirectMessageRepository directMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public DirectMessageController(UserRepository userRepository, FriendshipRepository friendshipRepository,
                                   DirectMessageRepository directMessageRepository,
                                   SimpMessagingTemplate messagingTemplate) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.directMessageRepository = directMessageRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @GetMapping
    public List<DirectMessageResponse> conversation(@PathVariable String friendId, Authentication authentication) {
        String userId = authentication.getName();
        requireFriendship(userId, friendId);

        return directMessageRepository.findConversation(userId, friendId).stream()
                .map(m -> new DirectMessageResponse(m.getId(), m.getSender().getId(), m.getText(), m.getSentAt().toString()))
                .toList();
    }

    @PostMapping
    public DirectMessageResponse send(@PathVariable String friendId, @RequestBody SendDirectMessageRequest request,
                                      Authentication authentication) {
        String userId = authentication.getName();
        requireFriendship(userId, friendId);

        String text = request.text() == null ? "" : request.text().trim();
        if (text.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mensaje no puede estar vacío");
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            text = text.substring(0, MAX_MESSAGE_LENGTH);
        }

        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        User recipient = userRepository.findById(friendId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        DirectMessage saved = directMessageRepository.save(new DirectMessage(sender, recipient, text));

        // Si el destinatario está desconectado, no hay nadie escuchando este topic ahora
        // mismo y no pasa nada — el mensaje ya está guardado, lo verá en su historial de
        // conversación la próxima vez que la abra (ver conversation() arriba).
        messagingTemplate.convertAndSend(
                "/topic/user/%s".formatted(friendId),
                new DirectMessageNotification(saved.getId(), userId, sender.getUsername(), text, saved.getSentAt().toString()));

        return new DirectMessageResponse(saved.getId(), userId, text, saved.getSentAt().toString());
    }

    private void requireFriendship(String userId, String friendId) {
        Optional<Friendship> friendship = friendshipRepository.findBetween(userId, friendId);
        if (friendship.isEmpty() || !Friendship.STATUS_ACCEPTED.equals(friendship.get().getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes escribir a tus amigos");
        }
    }
}
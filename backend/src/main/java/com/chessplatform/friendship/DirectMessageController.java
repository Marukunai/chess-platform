package com.chessplatform.friendship;

import com.chessplatform.friendship.dto.ConversationSummaryResponse;
import com.chessplatform.friendship.dto.DirectMessageNotification;
import com.chessplatform.friendship.dto.DirectMessageResponse;
import com.chessplatform.friendship.dto.MessagesReadNotification;
import com.chessplatform.friendship.dto.SendDirectMessageRequest;
import com.chessplatform.persistence.entity.DirectMessage;
import com.chessplatform.persistence.entity.Friendship;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.DirectMessageRepository;
import com.chessplatform.persistence.repository.FriendshipRepository;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.presence.PresenceService;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Mensajes privados entre amigos — a diferencia del chat de partida (efímero, solo
 * retransmisión), esto se guarda de verdad, por eso vive aparte en su propio
 * controlador en vez de reutilizar GameWebSocketController. Bajo /api/friends, como el
 * resto de lo de amistad — necesita identidad igual que todo lo demás ahí.
 *
 * Sin @RequestMapping a nivel de clase a propósito: /api/friends/conversations (no
 * cuelga de ningún {friendId} en concreto) convive aquí con
 * /api/friends/{friendId}/messages porque las dos cosas son "mensajería directa", la
 * misma responsabilidad — separarlas en dos controladores solo porque una ruta tiene
 * variable de plantilla y la otra no sería una división artificial.
 */
@RestController
public class DirectMessageController {

    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final DirectMessageRepository directMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;

    public DirectMessageController(UserRepository userRepository, FriendshipRepository friendshipRepository,
                                   DirectMessageRepository directMessageRepository,
                                   SimpMessagingTemplate messagingTemplate, PresenceService presenceService) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.directMessageRepository = directMessageRepository;
        this.messagingTemplate = messagingTemplate;
        this.presenceService = presenceService;
    }

    /**
     * Todos los amigos, cada uno con su última conversación si la hay — pensado para el
     * desplegable general de chat: una sola llamada da tanto "con quién tengo
     * conversaciones recientes" como "a quién más le puedo escribir aunque no le haya
     * escrito nunca" (sale igual en la lista, solo que sin previsualización ni fecha).
     * Sin mensajes propios, primero los que tienen algo sin leer, luego por fecha del
     * último mensaje más reciente, y los que nunca han tenido conversación al final.
     */
    @GetMapping("/api/friends/conversations")
    public List<ConversationSummaryResponse> conversations(Authentication authentication) {
        String userId = authentication.getName();

        return friendshipRepository.findAcceptedFriendships(userId).stream()
                .map(f -> {
                    User friend = f.theOtherUser(userId);
                    List<DirectMessage> messages = directMessageRepository.findConversation(userId, friend.getId());
                    DirectMessage last = messages.isEmpty() ? null : messages.getLast();
                    long unreadCount = messages.stream()
                            .filter(m -> !m.isRead() && m.getRecipient().getId().equals(userId))
                            .count();

                    return new ConversationSummaryResponse(
                            friend.getId(), friend.getUsername(), friend.getAvatarUrl(),
                            presenceService.statusOf(friend.getId()),
                            last == null ? null : last.getText(),
                            last == null ? null : last.getSentAt().toString(),
                            (int) unreadCount
                    );
                })
                .sorted(Comparator
                        // Primero quien tiene algo sin leer, sea cual sea la fecha.
                        .comparing((ConversationSummaryResponse c) -> c.unreadCount() > 0).reversed()
                        // Luego quien tiene conversación (con o sin leer) antes que quien nunca ha hablado contigo.
                        .thenComparing(c -> c.lastMessageAt() != null, Comparator.reverseOrder())
                        // Y entre los que sí tienen conversación, el mensaje más reciente primero — comparar
                        // el string ISO-8601 tal cual funciona porque ese formato es "ordenable
                        // lexicográficamente" por diseño (año-mes-día-hora, de mayor a menor peso, con
                        // ancho fijo), no hace falta volver a parsearlo a Instant solo para ordenar.
                        .thenComparing(c -> c.lastMessageAt() == null ? "" : c.lastMessageAt(),
                                Comparator.reverseOrder()))
                .toList();
    }

    @GetMapping("/api/friends/{friendId}/messages")
    public List<DirectMessageResponse> conversation(@PathVariable String friendId, Authentication authentication) {
        String userId = authentication.getName();
        requireFriendship(userId, friendId);

        // Pedir la conversación ES verla — de paso se marcan como leídos los mensajes
        // que llegaron mientras tanto (ver markUnreadMessagesAsRead). Esto cubre abrir
        // el chat de cero; para un mensaje que llega en directo mientras la conversación
        // YA está abierta hay un camino aparte, ver markConversationAsRead() más abajo —
        // ese caso no pasa por aquí, porque no hay ningún nuevo GET de por medio.
        List<DirectMessage> messages = directMessageRepository.findConversation(userId, friendId);
        markUnreadMessagesAsRead(userId, friendId, messages);

        return messages.stream()
                .map(m -> new DirectMessageResponse(m.getId(), m.getSender().getId(), m.getText(), m.getSentAt().toString(), m.isRead()))
                .toList();
    }

    /**
     * Para cuando la conversación YA está abierta y llega un mensaje nuevo en directo
     * por WebSocket — el cliente ya lo añade a la pantalla él solo (ver
     * DirectMessageNotification), pero eso no pasa por ningún GET que lo marque leído
     * en el servidor. Sin esto, alguien mirando la conversación en ese mismo instante
     * seguiría viendo crecer su contador de no leídos por mensajes que ya está mirando.
     */
    @PostMapping("/api/friends/{friendId}/messages/read")
    public void markConversationAsRead(@PathVariable String friendId, Authentication authentication) {
        String userId = authentication.getName();
        requireFriendship(userId, friendId);
        markUnreadMessagesAsRead(userId, friendId, directMessageRepository.findConversation(userId, friendId));
    }

    private void markUnreadMessagesAsRead(String userId, String friendId, List<DirectMessage> messages) {
        List<DirectMessage> newlyRead = messages.stream()
                .filter(m -> !m.isRead() && m.getRecipient().getId().equals(userId))
                .toList();
        if (!newlyRead.isEmpty()) {
            newlyRead.forEach(DirectMessage::markAsRead);
            directMessageRepository.saveAll(newlyRead);

            // Todos esos mensajes los mandó la misma persona (friendId, el otro lado de
            // esta conversación de dos) — le avisamos para que pueda ver "Leído" en su
            // propia pantalla sin tener que volver a abrir nada.
            messagingTemplate.convertAndSend(
                    "/topic/user/%s".formatted(friendId),
                    new MessagesReadNotification(userId));
        }
    }

    @PostMapping("/api/friends/{friendId}/messages")
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

        // El mensaje se guarda igualmente aunque el destinatario tenga "no molestar" —
        // solo se silencia el AVISO en vivo, no la entrega en sí. Lo verá en su
        // historial de conversación la próxima vez que la abra (ver conversation()
        // arriba), igual que si estuviera desconectado.
        if (!presenceService.isDoNotDisturb(friendId)) {
            messagingTemplate.convertAndSend(
                    "/topic/user/%s".formatted(friendId),
                    new DirectMessageNotification(saved.getId(), userId, sender.getUsername(), text, saved.getSentAt().toString()));
        }

        return new DirectMessageResponse(saved.getId(), userId, text, saved.getSentAt().toString(), saved.isRead());
    }

    private void requireFriendship(String userId, String friendId) {
        Optional<Friendship> friendship = friendshipRepository.findBetween(userId, friendId);
        if (friendship.isEmpty() || !Friendship.STATUS_ACCEPTED.equals(friendship.get().getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes escribir a tus amigos");
        }
    }
}
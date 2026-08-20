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
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Todo bajo /api/friends necesita identidad de verdad — a diferencia de /api/users/**,
 * aquí no hay ningún GET público: hasta buscar usuarios depende de saber quién pregunta,
 * para poder calcular su relación con cada resultado (ver friendshipStatusBetween). No
 * hace falta tocar SecurityConfig para esto — al no coincidir con ninguna de las rutas
 * permitAll ya existentes, cae directo en el .anyRequest().authenticated() genérico.
 */
@RestController
@RequestMapping("/api/friends")
public class FriendshipController {

    private static final int MIN_SEARCH_LENGTH = 2;

    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;

    public FriendshipController(UserRepository userRepository, FriendshipRepository friendshipRepository,
                                SimpMessagingTemplate messagingTemplate, PresenceService presenceService) {
        this.userRepository = userRepository;
        this.friendshipRepository = friendshipRepository;
        this.messagingTemplate = messagingTemplate;
        this.presenceService = presenceService;
    }

    @GetMapping("/search")
    public List<UserSearchResultResponse> search(@RequestParam String q, Authentication authentication) {
        String viewerId = authentication.getName();
        String query = q == null ? "" : q.trim();
        // Con menos de dos caracteres, "LIKE %x%" devolvería medio directorio de
        // usuarios de golpe — no es útil ni barato, mejor no buscar nada todavía.
        if (query.length() < MIN_SEARCH_LENGTH) {
            return List.of();
        }

        return userRepository.findTop20ByUsernameContainingIgnoreCaseAndDeletedAtIsNull(query).stream()
                .filter(user -> !user.getId().equals(viewerId)) // no te muestres a ti mismo en tu propia búsqueda
                .map(user -> new UserSearchResultResponse(
                        user.getId(), user.getUsername(), user.getAvatarUrl(),
                        friendshipStatusBetween(viewerId, user.getId())))
                .toList();
    }

    @PostMapping("/requests/{targetUserId}")
    public void sendRequest(@PathVariable String targetUserId, Authentication authentication) {
        String requesterId = authentication.getName();
        if (requesterId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes enviarte una solicitud a ti mismo");
        }

        User requester = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        if (friendshipRepository.findBetween(requesterId, targetUserId).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ya existe una solicitud o una amistad con este usuario");
        }

        friendshipRepository.save(new Friendship(requester, target));

        if (!presenceService.isDoNotDisturb(targetUserId)) {
            messagingTemplate.convertAndSend(
                    "/topic/user/%s".formatted(targetUserId),
                    new FriendRequestNotification(requesterId, requester.getUsername()));
        }
    }

    /** Solo las que ME HAN LLEGADO (soy el addressee) — las que yo mismo envié no aparecen aquí. */
    @GetMapping("/requests")
    public List<FriendRequestResponse> pendingRequests(Authentication authentication) {
        return friendshipRepository.findByAddressee_IdAndStatus(authentication.getName(), Friendship.STATUS_PENDING)
                .stream()
                .map(f -> new FriendRequestResponse(
                        f.getId(), f.getRequester().getId(), f.getRequester().getUsername(),
                        f.getRequester().getAvatarUrl()))
                .toList();
    }

    @PutMapping("/requests/{friendshipId}")
    public void respondToRequest(@PathVariable String friendshipId, @RequestBody RespondFriendRequestRequest request,
                                 Authentication authentication) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Solicitud no encontrada"));

        if (!friendship.getAddressee().getId().equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo el destinatario puede responder a esta solicitud");
        }
        if (!friendship.isPending()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta solicitud ya se respondió");
        }

        if (request.accept()) {
            friendship.accept();
            friendshipRepository.save(friendship);
            String requesterId = friendship.getRequester().getId();
            if (!presenceService.isDoNotDisturb(requesterId)) {
                messagingTemplate.convertAndSend(
                        "/topic/user/%s".formatted(requesterId),
                        new FriendRequestAcceptedNotification(
                                friendship.getAddressee().getId(), friendship.getAddressee().getUsername()));
            }
        } else {
            friendshipRepository.delete(friendship);
        }
    }

    @GetMapping
    public List<FriendResponse> friends(Authentication authentication) {
        String userId = authentication.getName();
        return friendshipRepository.findAcceptedFriendships(userId).stream()
                .map(f -> {
                    User friend = f.theOtherUser(userId);
                    return new FriendResponse(friend.getId(), friend.getUsername(), friend.getAvatarUrl(),
                            presenceService.statusOf(friend.getId()));
                })
                .toList();
    }

    private String friendshipStatusBetween(String viewerId, String otherId) {
        Optional<Friendship> friendship = friendshipRepository.findBetween(viewerId, otherId);
        if (friendship.isEmpty()) {
            return "NONE";
        }
        Friendship f = friendship.get();
        if (!f.isPending()) {
            return "FRIENDS";
        }
        return f.getRequester().getId().equals(viewerId) ? "PENDING_SENT" : "PENDING_RECEIVED";
    }
}
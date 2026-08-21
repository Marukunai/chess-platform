package com.chessplatform.matchmaking.controller;

import com.chessplatform.matchmaking.MatchmakingQueue;
import com.chessplatform.matchmaking.TimeControl;
import com.chessplatform.matchmaking.dto.MatchmakingJoinMessage;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.rating.GameMode;
import com.chessplatform.rating.GlickoRatingService;
import com.chessplatform.rating.UserRatingService;
import com.chessplatform.realtime.dto.ErrorMessage;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Punto de entrada STOMP para unirse/salir de la cola de matchmaking. El resultado
 * (emparejado o no) llega de forma asíncrona por /topic/matchmaking/{playerId} — ver
 * MatchmakingService, que es quien de verdad empareja en su @Scheduled tick().
 */
@Controller
public class MatchmakingController {

    private final MatchmakingQueue queue;
    private final UserRepository userRepository;
    private final UserRatingService userRatingService;
    private final SimpMessagingTemplate messagingTemplate;

    public MatchmakingController(MatchmakingQueue queue, UserRepository userRepository,
                                 UserRatingService userRatingService, SimpMessagingTemplate messagingTemplate) {
        this.queue = queue;
        this.userRepository = userRepository;
        this.userRatingService = userRatingService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/matchmaking/join")
    public void join(MatchmakingJoinMessage message, Principal principal) {
        if (principal == null) {
            return; // sin identidad verificada (JWT en el CONNECT) no se puede entrar a la cola
        }

        TimeControl timeControl;
        try {
            timeControl = message.toTimeControl();
        } catch (IllegalArgumentException e) {
            messagingTemplate.convertAndSend(
                    "/topic/matchmaking/%s".formatted(principal.getName()),
                    new ErrorMessage("INVALID_TIME_CONTROL", e.getMessage())
            );
            return;
        }

        // El rating sale del usuario ya autenticado, nunca de lo que mande el cliente
        // sobre sí mismo — mismo principio que ya aplicamos en GameWebSocketController.
        // Y del rating EN ESTA MODALIDAD concreta, no de un rating único — toTimeControl()
        // solo puede devolver uno de los cuatro presets conocidos, así que
        // GameMode.valueOf() sobre su nombre nunca falla aquí.
        var user = userRepository.findById(principal.getName());
        GameMode mode = GameMode.valueOf(TimeControl.presetNameFor(timeControl.initialTime(), timeControl.increment()).orElseThrow());
        int rating = user.map(u -> (int) Math.round(userRatingService.findOrDefault(u, mode).getRating()))
                .orElse((int) GlickoRatingService.DEFAULT_RATING);
        String username = user.map(User::getUsername).orElse(principal.getName());
        String avatarUrl = user.map(User::getAvatarUrl).orElse(null);

        queue.enqueue(principal.getName(), username, avatarUrl, rating, timeControl);
    }

    @MessageMapping("/matchmaking/leave")
    public void leave(Principal principal) {
        if (principal != null) {
            queue.remove(principal.getName());
        }
    }
}
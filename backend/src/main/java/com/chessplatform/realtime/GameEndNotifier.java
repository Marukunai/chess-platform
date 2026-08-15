package com.chessplatform.realtime;

import com.chessplatform.rating.GameResultRecorder;
import com.chessplatform.rating.GameResultRecorder.RatingChanges;
import com.chessplatform.realtime.dto.GameOverMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Único punto por el que se anuncia el fin de una partida, se registra su resultado
 * (rating + historial, ver GameResultRecorder) y se limpia del registro — usado desde
 * GameWebSocketController (jaque mate, ahogado, rendición) y desde GameTimeoutService
 * (bandera caída), para que las cuatro formas de terminar una partida pasen siempre por
 * la misma lógica.
 */
@Component
public class GameEndNotifier {

    private static final Logger log = LoggerFactory.getLogger(GameEndNotifier.class);

    private final GameSessionRegistry sessionRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameResultRecorder gameResultRecorder;

    public GameEndNotifier(GameSessionRegistry sessionRegistry, SimpMessagingTemplate messagingTemplate,
                           GameResultRecorder gameResultRecorder) {
        this.sessionRegistry = sessionRegistry;
        this.messagingTemplate = messagingTemplate;
        this.gameResultRecorder = gameResultRecorder;
    }

    /**
     * @param result "1-0" | "0-1" | "1/2-1/2"
     * @param reason "checkmate" | "stalemate" | "resignation" | "timeout"
     */
    public void endGame(GameSession session, String result, String reason) {
        Optional<RatingChanges> ratingChanges = Optional.empty();
        try {
            ratingChanges = gameResultRecorder.record(session, result);
        } catch (RuntimeException e) {
            // Un fallo al guardar (p. ej. la base de datos caída) no debería impedir que
            // los jugadores se enteren de que la partida ha terminado — es peor dejarlos
            // esperando indefinidamente que perder el guardado de esta partida concreta.
            log.error("No se pudo registrar el resultado de la partida {}", session.gameId(), e);
        }

        messagingTemplate.convertAndSend(
                "/topic/game/%s".formatted(session.gameId()),
                new GameOverMessage(
                        session.gameId(), result, reason,
                        ratingChanges.map(RatingChanges::whiteChange).orElse(null),
                        ratingChanges.map(RatingChanges::blackChange).orElse(null)
                )
        );
        sessionRegistry.remove(session.gameId());
    }
}
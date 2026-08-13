package com.chessplatform.realtime;

import com.chessplatform.realtime.dto.GameOverMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Único punto por el que se anuncia el fin de una partida y se limpia del registro —
 * usado desde GameWebSocketController (jaque mate, ahogado, rendición) y desde
 * GameTimeoutService (bandera caída), para que las cuatro formas de terminar una
 * partida pasen siempre por la misma lógica. Cuando llegue Glicko-2 + persistencia,
 * este es el sitio natural para engancharlos — cubre ya los cuatro casos.
 */
@Component
public class GameEndNotifier {

    private final GameSessionRegistry sessionRegistry;
    private final SimpMessagingTemplate messagingTemplate;

    public GameEndNotifier(GameSessionRegistry sessionRegistry, SimpMessagingTemplate messagingTemplate) {
        this.sessionRegistry = sessionRegistry;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * @param result "1-0" | "0-1" | "1/2-1/2"
     * @param reason "checkmate" | "stalemate" | "resignation" | "timeout"
     */
    public void endGame(GameSession session, String result, String reason) {
        messagingTemplate.convertAndSend(
                "/topic/game/%s".formatted(session.gameId()),
                new GameOverMessage(session.gameId(), result, reason)
        );
        sessionRegistry.remove(session.gameId());
    }
}
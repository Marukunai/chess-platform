package com.chessplatform.realtime.controller;

import com.chessplatform.realtime.GameSessionRegistry;
import com.chessplatform.realtime.dto.ErrorMessage;
import com.chessplatform.realtime.dto.GameStateSyncMessage;
import com.chessplatform.realtime.dto.MoveMessage;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * Punto de entrada STOMP para mensajes de partida.
 *
 * Los clientes se suscriben a /topic/game/{gameId} y envían jugadas a
 * /app/game/{gameId}/move. De momento son handlers de ejemplo (Fase 1 en progreso):
 * validan la forma del mensaje pero delegan la lógica real de aplicar la jugada al motor
 * de reglas, que aún está pendiente de completar (ver Board.applyMove).
 */
@Controller
public class GameWebSocketController {

    private final GameSessionRegistry sessionRegistry;
    private final SimpMessagingTemplate messagingTemplate;

    public GameWebSocketController(GameSessionRegistry sessionRegistry,
                                    SimpMessagingTemplate messagingTemplate) {
        this.sessionRegistry = sessionRegistry;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/game/{gameId}/move")
    public void handleMove(@DestinationVariable String gameId, MoveMessage move) {
        var session = sessionRegistry.find(gameId);
        if (session.isEmpty()) {
            messagingTemplate.convertAndSend(
                    "/topic/game/%s".formatted(gameId),
                    new ErrorMessage("GAME_NOT_FOUND", "No existe una partida activa con id " + gameId)
            );
            return;
        }

        // TODO (Fase 1):
        // 1. Validar que move.from()/move.to() son un movimiento legal (Board.legalMoves())
        // 2. Aplicar la jugada (Board.applyMove())
        // 3. Actualizar el reloj
        // 4. Comprobar fin de partida (jaque mate / ahogado / tablas)
        // 5. Difundir el nuevo estado a /topic/game/{gameId}

        messagingTemplate.convertAndSend(
                "/topic/game/%s".formatted(gameId),
                new ErrorMessage("NOT_IMPLEMENTED", "Aplicación de jugadas pendiente de implementar")
        );
    }

    @MessageMapping("/game/{gameId}/join")
    @SendTo("/topic/game/{gameId}")
    public GameStateSyncMessage handleJoin(@DestinationVariable String gameId) {
        // TODO (Fase 1): sincronizar el estado real de la partida al jugador que se
        // conecta/reconecta, usando GameSessionRegistry + FEN real desde Board.
        return new GameStateSyncMessage(
                gameId,
                "startpos",
                "white",
                0L,
                0L,
                List.of(),
                "PENDING_IMPLEMENTATION"
        );
    }
}

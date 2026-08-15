package com.chessplatform.realtime.controller;

import com.chessplatform.engine.Board;
import com.chessplatform.engine.Color;
import com.chessplatform.engine.Move;
import com.chessplatform.realtime.GameEndNotifier;
import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
import com.chessplatform.realtime.dto.ErrorMessage;
import com.chessplatform.realtime.dto.GameStateSyncMessage;
import com.chessplatform.realtime.dto.MoveMessage;
import com.chessplatform.realtime.dto.ResignMessage;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

/**
 * Punto de entrada STOMP para mensajes de partida.
 *
 * Los clientes se suscriben a /topic/game/{gameId} y envían jugadas a
 * /app/game/{gameId}/move. El Principal de cada método lo resuelve Spring
 * automáticamente a partir de lo que StompAuthChannelInterceptor fijó durante el CONNECT
 * (ver realtime/config) — no hace falta revalidar el token aquí, solo comprobar que la
 * identidad ya verificada corresponde al jugador que debería estar moviendo.
 */
@Controller
public class GameWebSocketController {

    private final GameSessionRegistry sessionRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameEndNotifier gameEndNotifier;

    public GameWebSocketController(GameSessionRegistry sessionRegistry,
                                   SimpMessagingTemplate messagingTemplate,
                                   GameEndNotifier gameEndNotifier) {
        this.sessionRegistry = sessionRegistry;
        this.messagingTemplate = messagingTemplate;
        this.gameEndNotifier = gameEndNotifier;
    }

    @MessageMapping("/game/{gameId}/move")
    public void handleMove(@DestinationVariable String gameId, MoveMessage message, Principal principal) {
        Optional<GameSession> maybeSession = sessionRegistry.find(gameId);
        if (maybeSession.isEmpty()) {
            sendError(gameId, "GAME_NOT_FOUND", "No existe una partida activa con id " + gameId);
            return;
        }
        GameSession session = maybeSession.get();

        // Sincronizado sobre la propia partida (ver javadoc de GameSession): sin esto,
        // dos jugadas casi simultáneas para la misma partida podrían pasar la
        // comprobación de legalidad las dos ANTES de que cualquiera mute el tablero, y
        // aplicarse ambas. El bloqueo es por partida, no global — otras partidas no se
        // ven afectadas.
        synchronized (session) {
            if (!isPlayersTurn(session, principal)) {
                sendError(gameId, "NOT_YOUR_TURN", "No puedes mover: no es tu turno o no perteneces a esta partida");
                return;
            }

            Move move;
            try {
                move = message.toEngineMove();
            } catch (IllegalArgumentException e) {
                sendError(gameId, "MALFORMED_MOVE", "No se pudo interpretar la jugada: " + e.getMessage());
                return;
            }

            if (!session.board().legalMoves().contains(move)) {
                sendError(gameId, "ILLEGAL_MOVE", "Jugada no legal en la posición actual: " + move.toUci());
                return;
            }

            session.applyMove(move);
            broadcastUpdatedState(session);
        }
    }

    @MessageMapping("/game/{gameId}/join")
    public void handleJoin(@DestinationVariable String gameId) {
        Optional<GameSession> maybeSession = sessionRegistry.find(gameId);
        if (maybeSession.isEmpty()) {
            sendError(gameId, "GAME_NOT_FOUND", "No existe una partida activa con id " + gameId);
            return;
        }
        GameSession session = maybeSession.get();
        synchronized (session) {
            broadcastUpdatedState(session);
        }
    }

    @MessageMapping("/game/{gameId}/resign")
    public void handleResign(@DestinationVariable String gameId, ResignMessage message, Principal principal) {
        Optional<GameSession> maybeSession = sessionRegistry.find(gameId);
        if (maybeSession.isEmpty()) {
            sendError(gameId, "GAME_NOT_FOUND", "No existe una partida activa con id " + gameId);
            return;
        }
        GameSession session = maybeSession.get();

        synchronized (session) {
            String userId = principal != null ? principal.getName() : null;
            boolean whiteResigns = session.whitePlayerId().equals(userId);
            boolean blackResigns = session.blackPlayerId().equals(userId);
            if (!whiteResigns && !blackResigns) {
                sendError(gameId, "FORBIDDEN", "No perteneces a esta partida");
                return;
            }

            String result = whiteResigns ? "0-1" : "1-0";
            gameEndNotifier.endGame(session, result, "resignation");
        }
    }

    /**
     * ¿La identidad ya verificada en `principal` corresponde al jugador a quien le toca
     * mover ahora mismo? Cubre a la vez dos casos con una sola comprobación: un jugador
     * intentando mover fuera de su turno, y alguien ajeno a la partida intentando mover
     * sin más — ninguno de los dos coincidirá nunca con expectedPlayerId.
     */
    private boolean isPlayersTurn(GameSession session, Principal principal) {
        if (principal == null) {
            return false;
        }
        String expectedPlayerId = session.board().turn() == Color.WHITE
                ? session.whitePlayerId()
                : session.blackPlayerId();
        return expectedPlayerId.equals(principal.getName());
    }

    /**
     * Calcula legalMoves() e isInCheck() UNA sola vez, manda SIEMPRE el estado
     * actualizado (para que el cliente vea la jugada que se acaba de hacer — incluida la
     * que da jaque mate — antes de que llegue el aviso de fin de partida) y decide con
     * eso si además hay que terminar la partida. Reutilizado tanto tras aplicar una
     * jugada como al unirse a una partida ya en curso.
     */
    private void broadcastUpdatedState(GameSession session) {
        Board board = session.board();
        String gameId = session.gameId();

        List<Move> legalMoves = board.legalMoves();
        boolean inCheck = board.isInCheck(board.turn());

        // "+"/"#" en la notación de la última jugada — se calcula aquí, reutilizando el
        // legalMoves()/isInCheck() que de todas formas hace falta para decidir el fin de
        // partida, en vez de que Board tenga que repetir ese cálculo (caro: simula cada
        // jugada pseudo-legal) solo para anotar la notación en cada jugada.
        if (inCheck) {
            board.annotateLastMove(legalMoves.isEmpty() ? "#" : "+");
        }

        messagingTemplate.convertAndSend(
                "/topic/game/%s".formatted(gameId),
                GameStateSyncMessage.from(session, legalMoves, inCheck)
        );

        if (board.isDrawByFiftyMoveRule()) {
            gameEndNotifier.endGame(session, "1/2-1/2", "fifty-move-rule");
            return;
        }
        if (board.isDrawByRepetition()) {
            gameEndNotifier.endGame(session, "1/2-1/2", "threefold-repetition");
            return;
        }
        if (legalMoves.isEmpty()) {
            String result = inCheck
                    ? (board.turn() == Color.WHITE ? "0-1" : "1-0")
                    : "1/2-1/2";
            String reason = inCheck ? "checkmate" : "stalemate";

            gameEndNotifier.endGame(session, result, reason);
        }
    }

    private void sendError(String gameId, String code, String message) {
        messagingTemplate.convertAndSend(
                "/topic/game/%s".formatted(gameId),
                new ErrorMessage(code, message)
        );
    }
}
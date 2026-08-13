package com.chessplatform.realtime.controller;

import com.chessplatform.engine.Board;
import com.chessplatform.engine.Color;
import com.chessplatform.engine.Move;
import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
import com.chessplatform.realtime.dto.ErrorMessage;
import com.chessplatform.realtime.dto.GameOverMessage;
import com.chessplatform.realtime.dto.GameStateSyncMessage;
import com.chessplatform.realtime.dto.MoveMessage;
import com.chessplatform.realtime.dto.ResignMessage;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;
import java.util.Optional;

/**
 * Punto de entrada STOMP para mensajes de partida.
 *
 * Los clientes se suscriben a /topic/game/{gameId} y envían jugadas a
 * /app/game/{gameId}/move.
 *
 * Nota de seguridad (Fase 1, deuda técnica conocida y deliberada): todavía no se verifica
 * que el remitente de una jugada sea realmente el dueño de las piezas que mueve — eso
 * necesita resolver la identidad real vía el Principal de la sesión STOMP, que depende de
 * que auth/ (JWT + Spring Security) esté completado y conectado al handshake de
 * WebSocket. Por ahora cualquier cliente conectado a una partida puede mover cualquier
 * color. Se soluciona antes de exponer esto fuera de desarrollo local.
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
    public void handleMove(@DestinationVariable String gameId, MoveMessage message) {
        Optional<GameSession> maybeSession = sessionRegistry.find(gameId);
        if (maybeSession.isEmpty()) {
            sendError(gameId, "GAME_NOT_FOUND", "No existe una partida activa con id " + gameId);
            return;
        }
        GameSession session = maybeSession.get();

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

    @MessageMapping("/game/{gameId}/join")
    public void handleJoin(@DestinationVariable String gameId) {
        Optional<GameSession> maybeSession = sessionRegistry.find(gameId);
        if (maybeSession.isEmpty()) {
            sendError(gameId, "GAME_NOT_FOUND", "No existe una partida activa con id " + gameId);
            return;
        }
        broadcastUpdatedState(maybeSession.get());
    }

    @MessageMapping("/game/{gameId}/resign")
    public void handleResign(@DestinationVariable String gameId, ResignMessage message) {
        Optional<GameSession> maybeSession = sessionRegistry.find(gameId);
        if (maybeSession.isEmpty()) {
            sendError(gameId, "GAME_NOT_FOUND", "No existe una partida activa con id " + gameId);
            return;
        }
        GameSession session = maybeSession.get();

        boolean whiteResigns = session.whitePlayerId().equals(message.playerId());
        String result = whiteResigns ? "0-1" : "1-0";

        messagingTemplate.convertAndSend(
                "/topic/game/%s".formatted(gameId),
                new GameOverMessage(gameId, result, "resignation")
        );
        sessionRegistry.remove(gameId);
    }

    /**
     * Calcula legalMoves() e isInCheck() UNA sola vez y decide con eso si difundir el
     * nuevo estado o el fin de la partida — evita recalcular legalMoves() varias veces
     * (cada llamada simula todas las jugadas pseudo-legales, no es gratis). Reutilizado
     * tanto tras aplicar una jugada como al unirse a una partida ya en curso.
     */
    private void broadcastUpdatedState(GameSession session) {
        Board board = session.board();
        String gameId = session.gameId();
        List<Move> legalMoves = board.legalMoves();
        boolean inCheck = board.isInCheck(board.turn());

        if (legalMoves.isEmpty()) {
            String result = inCheck
                    ? (board.turn() == Color.WHITE ? "0-1" : "1-0")
                    : "1/2-1/2";
            String reason = inCheck ? "checkmate" : "stalemate";

            messagingTemplate.convertAndSend(
                    "/topic/game/%s".formatted(gameId),
                    new GameOverMessage(gameId, result, reason)
            );
            sessionRegistry.remove(gameId);
            return;
        }

        messagingTemplate.convertAndSend(
                "/topic/game/%s".formatted(gameId),
                GameStateSyncMessage.from(session, legalMoves, inCheck)
        );
    }

    private void sendError(String gameId, String code, String message) {
        messagingTemplate.convertAndSend(
                "/topic/game/%s".formatted(gameId),
                new ErrorMessage(code, message)
        );
    }
}
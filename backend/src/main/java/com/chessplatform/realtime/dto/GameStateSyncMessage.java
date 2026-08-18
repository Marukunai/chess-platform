package com.chessplatform.realtime.dto;

import com.chessplatform.engine.Board;
import com.chessplatform.engine.Color;
import com.chessplatform.engine.Move;
import com.chessplatform.realtime.GameSession;

import java.util.List;

/**
 * Estado completo de la partida enviado al cliente tras cada jugada o al reconectar.
 *
 * boardFen: representación FEN del tablero (más compacta para enviar por red que
 * serializar las 64 casillas). legalMovesUci: movimientos legales del jugador en turno,
 * en formato UCI (necesarios en el cliente para resaltar jugadas posibles y para
 * construir la jugada a enviar, ahí sí hace falta UCI en bruto). movesNotation: el
 * historial completo de la partida hasta ahora en notación legible (p. ej. "Rxf6", no
 * "d5f6") — no solo la última jugada, así el cliente puede reconstruir la planilla
 * completa de una sola vez, incluso si se reconecta a media partida. lastMoveUci: la
 * última jugada en bruto (p. ej. "d5f6"), o null si todavía no se ha jugado ninguna —
 * el cliente la usa para saber qué casilla animar al recibir el estado, algo que no se
 * puede sacar de forma fiable de movesNotation (la notación con coronación como
 * "exd8=Q" no tiene la casilla de destino en una posición fija dentro del texto).
 *
 * whitePlayerId/blackPlayerId: el cliente los necesita para poder pedir el perfil del
 * rival (vista rápida al hacer clic en su nombre) — sin esto, en partida solo se conoce
 * el nombre de usuario, no el id con el que consultar /api/users/{userId}. No es una
 * exposición nueva: estos mismos ids ya viajaban en GameOverMessage y en el historial.
 * whiteUsername/blackUsername/whiteAvatarUrl/blackAvatarUrl: quiénes juegan y su
 * avatar — se manda en cada sincronización (no solo al emparejar) para que estén
 * disponibles también al reconectar o recargar la página.
 */
public record GameStateSyncMessage(
        String gameId,
        String boardFen,
        String turn,
        long whiteTimeRemainingMs,
        long blackTimeRemainingMs,
        List<String> legalMovesUci,
        String status,
        List<String> movesNotation,
        String lastMoveUci,
        String whitePlayerId,
        String whiteUsername,
        String whiteAvatarUrl,
        String blackPlayerId,
        String blackUsername,
        String blackAvatarUrl
) {

    /**
     * Construye el mensaje a partir de una GameSession, recibiendo legalMoves/inCheck ya
     * calculados por el llamador — legalMoves() recorre pseudo-legales simulando cada uno
     * (ver Board.legalMoves()), así que evitamos recalcularlo varias veces en la misma
     * petición (el llamador ya lo necesita para decidir si la partida ha terminado).
     */
    public static GameStateSyncMessage from(GameSession session, List<Move> legalMoves, boolean inCheck) {
        Board board = session.board();
        List<String> legalMovesUci = legalMoves.stream().map(Move::toUci).toList();
        String status = inCheck ? "CHECK" : "IN_PROGRESS";
        List<Move> moveHistory = board.moveHistory();
        String lastMoveUci = moveHistory.isEmpty() ? null : moveHistory.getLast().toUci();

        return new GameStateSyncMessage(
                session.gameId(),
                board.toFen(),
                board.turn() == Color.WHITE ? "white" : "black",
                session.timeRemaining(Color.WHITE).toMillis(),
                session.timeRemaining(Color.BLACK).toMillis(),
                legalMovesUci,
                status,
                board.notationHistory(),
                lastMoveUci,
                session.whitePlayerId(),
                session.whiteUsername(),
                session.whiteAvatarUrl(),
                session.blackPlayerId(),
                session.blackUsername(),
                session.blackAvatarUrl()
        );
    }
}
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
 * en formato UCI (necesarios en el cliente para resaltar jugadas posibles).
 */
public record GameStateSyncMessage(
        String gameId,
        String boardFen,
        String turn,
        long whiteTimeRemainingMs,
        long blackTimeRemainingMs,
        List<String> legalMovesUci,
        String status
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

        return new GameStateSyncMessage(
                session.gameId(),
                board.toFen(),
                board.turn() == Color.WHITE ? "white" : "black",
                session.timeRemaining(Color.WHITE).toMillis(),
                session.timeRemaining(Color.BLACK).toMillis(),
                legalMovesUci,
                status
        );
    }
}
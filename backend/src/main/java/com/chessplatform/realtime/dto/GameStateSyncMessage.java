package com.chessplatform.realtime.dto;

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
}

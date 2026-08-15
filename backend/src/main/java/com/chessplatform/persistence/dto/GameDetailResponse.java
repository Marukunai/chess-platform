package com.chessplatform.persistence.dto;

import java.util.List;

/**
 * movesNotation: jugadas en notación legible ("Rxf6", no "d5f6"), útiles para la
 * planilla del cliente. fenPositions: una posición por jugada MÁS la inicial (longitud
 * = movesNotation.size() + 1) — esto es lo que de verdad usa el cliente para
 * reproducir, ya reconstruido por GameReplayService con el motor real, no algo que el
 * cliente tenga que recalcular.
 */
public record GameDetailResponse(
        String id,
        String whiteUsername,
        String blackUsername,
        String result,
        String timeControl,
        String playedAt,
        List<String> movesNotation,
        List<String> fenPositions
) {
}
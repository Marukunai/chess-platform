package com.chessplatform.persistence.dto;

import java.util.List;

/**
 * moves: jugadas en UCI, útiles para una lista de jugadas en el cliente ("1. e2e4 e7e5...").
 * fenPositions: una posición por jugada MÁS la inicial (longitud = moves.size() + 1) —
 * esto es lo que de verdad usa el cliente para reproducir, ya reconstruido por
 * GameReplayService con el motor real, no algo que el cliente tenga que recalcular.
 */
public record GameDetailResponse(
        String id,
        String whiteUsername,
        String blackUsername,
        String result,
        String timeControl,
        String playedAt,
        List<String> moves,
        List<String> fenPositions
) {
}
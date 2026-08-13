package com.chessplatform.persistence.dto;

/**
 * Una fila del historial de partidas de un usuario — sin las jugadas, eso solo hace
 * falta al abrir una partida concreta (ver GameDetailResponse).
 */
public record GameSummaryResponse(
        String id,
        String whiteUsername,
        String blackUsername,
        String result,
        String timeControl,
        String playedAt
) {
}
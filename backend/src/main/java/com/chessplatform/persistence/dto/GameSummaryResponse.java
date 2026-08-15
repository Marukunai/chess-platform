package com.chessplatform.persistence.dto;

/**
 * Una fila del historial de partidas de un usuario — sin las jugadas, eso solo hace
 * falta al abrir una partida concreta (ver GameDetailResponse).
 *
 * whiteUserId/blackUserId: además de los nombres (para mostrar), los IDs — el cliente
 * solo conoce su propio userId (viene del JWT, no del username), así que sin esto no
 * habría forma de saber si "el usuario actual" ganó o perdió una partida concreta para
 * colorearla en el historial.
 */
public record GameSummaryResponse(
        String id,
        String whiteUserId,
        String whiteUsername,
        String blackUserId,
        String blackUsername,
        String result,
        String timeControl,
        String playedAt
) {
}
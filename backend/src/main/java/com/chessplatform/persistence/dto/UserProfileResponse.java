package com.chessplatform.persistence.dto;

/**
 * wins/losses/draws se calculan a partir de las partidas guardadas de este usuario (ver
 * UserController) — no son un contador aparte que haya que mantener sincronizado, salen
 * directamente de la fuente de verdad (la tabla games).
 *
 * winsByCheckmate: de esas victorias, cuántas fueron dando jaque mate en el tablero — el
 * único desglose por motivo que se muestra en el perfil (a diferencia de rendición o
 * tiempo, que dicen más del rival o del reloj, un jaque mate propio sí dice algo de cómo
 * juegas). El resto de motivos solo se ve partida a partida en el historial, no aquí.
 */
public record UserProfileResponse(
        String userId,
        String username,
        int rating,
        int ratingDeviation,
        int gamesPlayed,
        int wins,
        int losses,
        int draws,
        int winsByCheckmate
) {
}
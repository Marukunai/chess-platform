package com.chessplatform.persistence.dto;

/**
 * wins/losses/draws se calculan a partir de las partidas guardadas de este usuario (ver
 * UserController) — no son un contador aparte que haya que mantener sincronizado, salen
 * directamente de la fuente de verdad (la tabla games).
 */
public record UserProfileResponse(
        String userId,
        String username,
        int rating,
        int ratingDeviation,
        int gamesPlayed,
        int wins,
        int losses,
        int draws
) {
}
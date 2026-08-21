package com.chessplatform.persistence.dto;

import java.util.List;

/**
 * wins/losses/draws se calculan a partir de las partidas guardadas de este usuario (ver
 * UserController) — no son un contador aparte que haya que mantener sincronizado, salen
 * directamente de la fuente de verdad (la tabla games).
 *
 * winsByCheckmate: de esas victorias, cuántas fueron dando jaque mate en el tablero — el
 * único desglose por motivo que se muestra en el perfil (a diferencia de rendición o
 * tiempo, que dicen más del rival o del reloj, un jaque mate propio sí dice algo de cómo
 * juegas). El resto de motivos solo se ve partida a partida en el historial, no aquí.
 *
 * recentOpponents: los últimos rivales DISTINTOS, más reciente primero, tope 5 — si has
 * jugado varias veces seguidas contra la misma persona solo aparece una vez, con la
 * partida más reciente entre las dos.
 *
 * winRatePercent: wins / gamesPlayed en tanto por ciento, redondeado — 0 si todavía no
 * has jugado ninguna partida (no null: es un dato agregado normal, no algo que falte
 * calcular). Las tablas cuentan como partida jugada pero no como victoria, igual que en
 * cualquier plataforma de ajedrez real.
 *
 * ratings: siempre las cuatro modalidades, en este orden — para quien nunca ha jugado
 * alguna en concreto, sale con los valores Glicko-2 por defecto (1500/350) SIN que eso
 * cree ninguna fila en base de datos solo por haberla consultado (ver
 * UserController.ratingsFor(), que no pasa por UserRatingService.findOrDefault() a
 * propósito para esto).
 */
public record UserProfileResponse(
        String userId,
        String username,
        String country,
        String avatarUrl,
        List<ModeRatingResponse> ratings,
        int gamesPlayed,
        int wins,
        int losses,
        int draws,
        int winsByCheckmate,
        int winRatePercent,
        List<RecentOpponent> recentOpponents
) {
    public record RecentOpponent(String userId, String username) {
    }

    public record ModeRatingResponse(String mode, int rating, int ratingDeviation) {
    }
}
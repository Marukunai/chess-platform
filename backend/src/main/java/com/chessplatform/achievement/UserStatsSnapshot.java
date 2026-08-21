package com.chessplatform.achievement;

import com.chessplatform.rating.GameMode;

import java.util.Set;

/**
 * Todo lo que hace falta saber de un usuario para calcular sus 20 logros, en una sola
 * foto — se calcula una vez (ver AchievementService.computeSnapshot()) y se evalúa
 * contra el catálogo entero, en vez de una consulta por cada logro. Varios logros
 * comparten el mismo campo (p. ej. "jugar 10 partidas" y "jugar 200 partidas" leen
 * gamesPlayed igual), así que separar el cálculo de la evaluación evita repetir
 * consultas.
 *
 * hasAvatarSet/hasCountrySet: booleanos, no contadores — los logros de perfil son
 * binarios ("¿lo has puesto o no?"), igual que los de "juega tu primera partida de
 * bullet" ya lo eran antes de esta ampliación.
 */
public record UserStatsSnapshot(
        int gamesPlayed,
        int gamesWon,
        int gamesLost,
        int gamesDrawn,
        int stalemateDraws,
        int checkmateWins,
        int friendsCount,
        int directMessagesSent,
        int directMessagesReceived,
        int distinctConversationPartners,
        int highestRating,
        Set<GameMode> modesPlayed,
        boolean hasAvatarSet,
        boolean hasCountrySet,
        int accountAgeDays
) {
}
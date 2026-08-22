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
 *
 * *BotWins: a propósito NO cuentan para gamesPlayed/gamesWon ni para ninguno de los
 * demás campos generales de arriba — son logros de práctica, aparte, ver
 * AchievementCategory.BOTS. hardBotBlitzWins/hardBotClassicalWins son un subconjunto de
 * hardBotWins (ganarle al bot en Difícil, además jugando esa modalidad en concreto).
 *
 * puzzlesSolved/puzzleRating: de UserPuzzleAttempt/UserPuzzleRating, no de Game — un
 * sistema completamente aparte (ver puzzle.PuzzleController). puzzleRating parte de
 * 1500 (el valor Glicko-2 por defecto) para quien nunca ha resuelto ninguno, igual que
 * highestRating ya hacía con el rating de partidas — ningún logro de umbral por encima
 * de 1500 se desbloquearía antes de tiempo por esto.
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
        int accountAgeDays,
        int easyBotWins,
        int mediumBotWins,
        int hardBotWins,
        int hardBotBlitzWins,
        int hardBotClassicalWins,
        int puzzlesSolved,
        int puzzleRating
) {
}
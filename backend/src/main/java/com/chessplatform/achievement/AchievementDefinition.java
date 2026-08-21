package com.chessplatform.achievement;

import java.util.function.ToIntFunction;

/**
 * Un logro del catálogo — id, nombre, descripción, a qué campo de UserStatsSnapshot
 * mira (progressExtractor) y a partir de qué valor se considera desbloqueado (target).
 *
 * progressExtractor puede devolver más que target sin problema (p. ej. alguien con 300
 * partidas jugadas para un logro de "juega 200") — progressFor() lo recorta con
 * Math.min() a propósito, para que el progreso mostrado nunca "se pase" de 100%.
 */
public record AchievementDefinition(
        String id,
        String name,
        String description,
        AchievementCategory category,
        int target,
        ToIntFunction<UserStatsSnapshot> progressExtractor
) {
    public int progressFor(UserStatsSnapshot snapshot) {
        return Math.min(progressExtractor.applyAsInt(snapshot), target);
    }

    public boolean isUnlockedFor(UserStatsSnapshot snapshot) {
        return progressExtractor.applyAsInt(snapshot) >= target;
    }
}
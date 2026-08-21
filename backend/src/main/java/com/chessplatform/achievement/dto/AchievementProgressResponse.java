package com.chessplatform.achievement.dto;

/**
 * category: "GENERAL" | "VICTORIAS" | "RATING" | "MODALIDADES" | "SOCIAL" | "PERFIL".
 * currentProgress nunca supera target — ver AchievementDefinition.progressFor().
 * unlockedAt: null si todavía no lo tienes. rarityPercent: qué % de cuentas activas lo
 * tiene, con un decimal. firstUnlockedByUsername: null si nadie lo tiene desbloqueado
 * todavía en toda la plataforma.
 */
public record AchievementProgressResponse(
        String id,
        String name,
        String description,
        String category,
        int currentProgress,
        int target,
        boolean unlocked,
        String unlockedAt,
        double rarityPercent,
        String firstUnlockedByUsername
) {
}
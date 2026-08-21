package com.chessplatform.achievement.dto;

/** category: "GENERAL" | "VICTORIAS" | "RATING" | "MODALIDADES" | "SOCIAL". currentProgress nunca supera target — ver AchievementDefinition.progressFor(). */
public record AchievementProgressResponse(
        String id,
        String name,
        String description,
        String category,
        int currentProgress,
        int target,
        boolean unlocked
) {
}
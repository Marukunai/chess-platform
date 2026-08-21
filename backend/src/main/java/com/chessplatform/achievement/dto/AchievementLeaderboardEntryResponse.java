package com.chessplatform.achievement.dto;

public record AchievementLeaderboardEntryResponse(
        int rank,
        String userId,
        String username,
        int unlockedCount,
        int totalCount
) {
}
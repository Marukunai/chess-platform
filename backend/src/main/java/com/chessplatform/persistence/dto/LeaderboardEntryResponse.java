package com.chessplatform.persistence.dto;

public record LeaderboardEntryResponse(
        int rank,
        String userId,
        String username,
        int rating
) {
}
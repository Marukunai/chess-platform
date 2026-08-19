package com.chessplatform.friendship.dto;

/** status: "ONLINE" | "OFFLINE" | "IN_GAME" | "DO_NOT_DISTURB" — ver PresenceService. */
public record FriendResponse(String userId, String username, String avatarUrl, String status) {
}
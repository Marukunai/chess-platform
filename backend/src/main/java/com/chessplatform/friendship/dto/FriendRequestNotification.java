package com.chessplatform.friendship.dto;

/** Enviado a /topic/user/{targetUserId} en cuanto alguien le manda una solicitud — mismo canal persistente que ya usa la revancha. */
public record FriendRequestNotification(String fromUserId, String fromUsername) {
}
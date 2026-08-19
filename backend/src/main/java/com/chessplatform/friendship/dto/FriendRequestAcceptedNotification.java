package com.chessplatform.friendship.dto;

/** Enviado a /topic/user/{requesterId} cuando el destinatario acepta su solicitud. */
public record FriendRequestAcceptedNotification(String byUserId, String byUsername) {
}
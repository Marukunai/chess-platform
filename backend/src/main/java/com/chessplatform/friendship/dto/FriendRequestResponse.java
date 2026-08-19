package com.chessplatform.friendship.dto;

/** Una solicitud pendiente que ME HA LLEGADO — friendshipId hace falta para poder responderla. */
public record FriendRequestResponse(String friendshipId, String fromUserId, String fromUsername, String fromAvatarUrl) {
}
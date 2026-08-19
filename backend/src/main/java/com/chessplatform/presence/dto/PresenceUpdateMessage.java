package com.chessplatform.presence.dto;

/**
 * Enviado a /topic/user/{friendId} cada vez que cambia el estado de uno de sus amigos —
 * mismo canal persistente que ya usan revancha y amistad.
 *
 * status: "ONLINE" | "OFFLINE" | "IN_GAME" | "DO_NOT_DISTURB"
 */
public record PresenceUpdateMessage(String userId, String status) {
}
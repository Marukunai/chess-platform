package com.chessplatform.friendship.dto;

/**
 * Un amigo, con la última conversación con él si la hay — pensado para el desplegable
 * general de chat, que necesita a la vez "con quién tengo conversaciones" y "a quién
 * más le puedo escribir" en una sola llamada.
 *
 * status: "ONLINE" | "OFFLINE" | "IN_GAME" | "DO_NOT_DISTURB" — ver PresenceService.
 * lastMessageText/lastMessageAt: null si nunca ha habido conversación con esta persona
 * (sigue apareciendo en la lista igualmente, solo que sin previsualización ni fecha).
 * unreadCount: mensajes que te ha mandado y que todavía no has abierto.
 */
public record ConversationSummaryResponse(
        String userId,
        String username,
        String avatarUrl,
        String status,
        String lastMessageText,
        String lastMessageAt,
        int unreadCount
) {
}
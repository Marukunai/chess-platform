package com.chessplatform.friendship.dto;

/**
 * friendshipStatus: "NONE" | "PENDING_SENT" | "PENDING_RECEIVED" | "FRIENDS" — le dice
 * al cliente qué botón mostrar para este resultado (Añadir / Ya enviada / Aceptar-Rechazar
 * / Ya sois amigos), calculado desde el punto de vista de quien busca, no un dato fijo
 * del usuario encontrado.
 */
public record UserSearchResultResponse(String userId, String username, String avatarUrl, String friendshipStatus) {
}
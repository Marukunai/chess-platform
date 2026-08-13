package com.chessplatform.matchmaking.dto;

/**
 * Enviado a /topic/matchmaking/{playerId} cuando MatchmakingService empareja a alguien.
 * color: "white" | "black" — el que le tocó a ESE jugador concreto en esta partida.
 */
public record MatchFoundMessage(String gameId, String color) {
}
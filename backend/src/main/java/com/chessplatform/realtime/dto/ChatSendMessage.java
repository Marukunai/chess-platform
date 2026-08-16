package com.chessplatform.realtime.dto;

/** Lo que manda el cliente a /app/game/{gameId}/chat — el servidor resuelve quién lo envía. */
public record ChatSendMessage(String text) {
}
package com.chessplatform.realtime.dto;

public record MoveMessage(String gameId, String from, String to, String promotionType) {
}

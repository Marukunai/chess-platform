package com.chessplatform.realtime.dto;

/**
 * result: "1-0" | "0-1" | "1/2-1/2"
 * reason: "checkmate" | "resignation" | "timeout" | "stalemate" | "draw_agreement" | ...
 */
public record GameOverMessage(String gameId, String result, String reason) {
}

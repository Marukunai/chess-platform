package com.chessplatform.realtime.dto;

/**
 * result: "1-0" | "0-1" | "1/2-1/2"
 * reason: "checkmate" | "resignation" | "timeout" | "stalemate" | "draw_agreement" | ...
 *
 * whiteRatingChange/blackRatingChange: nullable a propósito — si el guardado en base de
 * datos falló (ver GameEndNotifier: un fallo ahí no debe impedir que los jugadores se
 * enteren de que la partida terminó), simplemente no sabemos cuánto cambió el rating, y
 * mentir con un 0 sería peor que no decir nada.
 */
public record GameOverMessage(String gameId, String result, String reason,
                              Double whiteRatingChange, Double blackRatingChange) {
}
package com.chessplatform.realtime.dto;

/**
 * result: "1-0" | "0-1" | "1/2-1/2"
 * reason: "checkmate" | "resignation" | "timeout" | "stalemate" | "draw_agreement" | ...
 *
 * whiteRatingChange/blackRatingChange: nullable a propósito — si el guardado en base de
 * datos falló (ver GameEndNotifier: un fallo ahí no debe impedir que los jugadores se
 * enteren de que la partida terminó), simplemente no sabemos cuánto cambió el rating, y
 * mentir con un 0 sería peor que no decir nada.
 *
 * whitePlayerId/blackPlayerId/whiteUsername/blackUsername/timeControlPreset: quién jugó
 * y con qué modalidad — GameSession ya no existe en el registro para cuando el cliente
 * pudiera necesitarlos después (se elimina justo tras esto, ver GameEndNotifier), así
 * que van aquí para que el botón de "Revancha" tenga con qué proponerla sin depender de
 * una partida que ya no está. timeControlPreset es nullable: si por lo que sea las
 * duraciones no correspondieran a ningún preset conocido (no debería pasar, todas las
 * partidas nacen de uno), simplemente no se ofrece revancha con la misma modalidad.
 */
public record GameOverMessage(String gameId, String result, String reason,
                              Double whiteRatingChange, Double blackRatingChange,
                              String whitePlayerId, String whiteUsername,
                              String blackPlayerId, String blackUsername,
                              String timeControlPreset) {
}
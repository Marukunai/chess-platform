package com.chessplatform.realtime.dto;

/**
 * Estado actual de la oferta de tablas de una partida — se manda al ofrecer, al aceptar
 * implícitamente (vía GameOverMessage, no este) y al rechazar. Un único mensaje
 * representando el estado completo (no "oferta"/"rechazo" por separado) para que el
 * cliente no tenga que llevar la cuenta de nada: siempre puede fijar su UI a partir de
 * este valor solo.
 *
 * offerStatus: "offered_by_white" | "offered_by_black" | "none"
 */
public record DrawOfferMessage(String gameId, String offerStatus) {
}
package com.chessplatform.realtime.dto;

/**
 * gameId va tanto en el destino STOMP como aquí (mismo patrón que MoveMessage) — a
 * propósito NO lleva playerId: antes de auth/, el cliente decía "quién" se rendía, lo
 * cual era trivialmente falsificable. Ahora la identidad sale del Principal verificado
 * por StompAuthChannelInterceptor, no de nada que mande el cliente.
 */
public record ResignMessage(String gameId) {
}
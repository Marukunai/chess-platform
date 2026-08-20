package com.chessplatform.challenge.dto;

/**
 * Enviado a /topic/user/{targetId} al proponer un reto directo.
 *
 * challenge=true siempre — campo de desambiguación a propósito: tiene EXACTAMENTE los
 * mismos tres campos que RematchOfferMessage (fromUserId, fromUsername,
 * timeControlPreset), y sin este cuarto campo el cliente no tendría forma de saber cuál
 * de los dos es (ver el comentario de handleUserChannelMessage en main.js, que
 * distingue las formas de mensaje que llegan por ese mismo canal compartido).
 */
public record ChallengeOfferMessage(String fromUserId, String fromUsername, String timeControlPreset, boolean challenge) {
}
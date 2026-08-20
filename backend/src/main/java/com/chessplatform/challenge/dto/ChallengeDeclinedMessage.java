package com.chessplatform.challenge.dto;

/**
 * Enviado a /topic/user/{fromUserId} si el reto se rechaza.
 *
 * challenge=true siempre — mismo motivo de desambiguación que ChallengeOfferMessage:
 * comparte forma exacta con RematchDeclinedMessage (solo byUsername) si no fuera por
 * este campo.
 */
public record ChallengeDeclinedMessage(String byUsername, boolean challenge) {
}
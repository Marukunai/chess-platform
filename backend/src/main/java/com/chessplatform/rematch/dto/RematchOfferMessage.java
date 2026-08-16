package com.chessplatform.rematch.dto;

/** Enviado a /topic/user/{targetUserId} cuando alguien propone la revancha. */
public record RematchOfferMessage(String fromUserId, String fromUsername, String timeControlPreset) {
}
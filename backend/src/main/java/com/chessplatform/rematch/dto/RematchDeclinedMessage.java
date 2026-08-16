package com.chessplatform.rematch.dto;

/** Enviado a /topic/user/{proposerUserId} cuando el rival rechaza la revancha. */
public record RematchDeclinedMessage(String byUsername) {
}
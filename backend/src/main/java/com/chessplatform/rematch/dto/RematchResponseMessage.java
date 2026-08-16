package com.chessplatform.rematch.dto;

/** Enviado por el cliente a /app/rematch/respond. */
public record RematchResponseMessage(boolean accept) {
}
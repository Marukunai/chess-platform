package com.chessplatform.challenge.dto;

/** Lo que manda el cliente a /app/challenge/respond. */
public record ChallengeResponseMessage(boolean accept) {
}
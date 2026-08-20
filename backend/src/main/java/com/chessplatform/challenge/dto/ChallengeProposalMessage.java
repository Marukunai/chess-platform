package com.chessplatform.challenge.dto;

/** Lo que manda el cliente a /app/challenge/propose. */
public record ChallengeProposalMessage(String opponentUserId, String timeControlPreset) {
}
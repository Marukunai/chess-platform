package com.chessplatform.bot.dto;

/**
 * Lo que manda el cliente a /app/bot/play.
 *
 * difficulty: "EASY" | "MEDIUM" | "HARD" (ver BotDifficulty).
 * color: "white" | "black" | "random" (o cualquier otra cosa, incluido null) — cualquier
 * valor que no sea exactamente "white" o "black" se trata como "al azar", ver
 * PlayVsBotController.resolveHumanColor().
 * timeControlPreset: mismos cuatro valores que ya usa el emparejamiento normal
 * (MatchmakingJoinMessage) — "BULLET" | "BLITZ" | "RAPID" | "CLASSICAL".
 */
public record PlayVsBotRequest(String difficulty, String color, String timeControlPreset) {
}
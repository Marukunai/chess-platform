package com.chessplatform.achievement.dto;

/**
 * Enviado a /topic/user/{userId} justo en el momento en que se detecta un logro nuevo
 * — ver AchievementUnlockService.checkAndNotify(), que es quien decide cuándo mandar
 * esto. No comparte ningún campo con las demás formas que llegan por ese mismo canal
 * (revancha, reto, amistad, presencia, mensajes), así que no hace falta ningún campo de
 * desambiguación como sí hizo falta con ChallengeOfferMessage.
 */
public record AchievementUnlockedMessage(String achievementId, String name, String description) {
}
package com.chessplatform.friendship.dto;

/**
 * Enviado a /topic/user/{originalSenderId} en cuanto el destinatario marca como leídos
 * los mensajes que le mandaste — así puedes ver "Leído" en tu propia pantalla sin tener
 * que refrescar ni volver a abrir la conversación.
 */
public record MessagesReadNotification(String readByUserId) {
}
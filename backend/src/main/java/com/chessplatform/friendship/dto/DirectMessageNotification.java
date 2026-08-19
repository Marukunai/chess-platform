package com.chessplatform.friendship.dto;

/**
 * Enviado a /topic/user/{recipientId} en cuanto llega un mensaje — si el destinatario
 * está desconectado, simplemente no hay nadie escuchando ese topic en ese momento y el
 * mensaje no se pierde igualmente: ya está guardado en base de datos, lo verá en su
 * historial de conversación la próxima vez que la abra.
 */
public record DirectMessageNotification(String messageId, String fromUserId, String fromUsername,
                                        String text, String sentAt) {
}
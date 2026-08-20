package com.chessplatform.friendship.dto;

/** read: solo tiene sentido consultarlo para tus propios mensajes enviados — es "¿lo ha leído ya el destinatario?", no "¿lo he leído yo?". */
public record DirectMessageResponse(String id, String senderUserId, String text, String sentAt, boolean read) {
}
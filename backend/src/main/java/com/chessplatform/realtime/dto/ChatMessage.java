package com.chessplatform.realtime.dto;

/**
 * Un mensaje de chat entre los dos jugadores de una partida — puramente de retransmisión,
 * no se guarda en ningún sitio (desaparece con la partida, igual que las flechas
 * dibujadas en el tablero). senderUsername en vez de senderUserId: el cliente lo pinta
 * directamente sin tener que resolver un id a un nombre.
 */
public record ChatMessage(String senderUsername, String text) {
}
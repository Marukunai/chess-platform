package com.chessplatform.rematch.dto;

/**
 * Enviado por el cliente a /app/rematch/propose.
 *
 * myColorInPreviousGame: "white" | "black" — el color que tenía QUIEN PROPONE en la
 * partida que acaba de terminar (el cliente lo sabe de sobra, venía en el
 * GameOverMessage). El servidor lo usa para intercambiar los colores en la revancha sin
 * tener que ir a buscar la partida anterior, que además ya no existe en memoria.
 */
public record RematchProposalMessage(String opponentUserId, String timeControlPreset, String myColorInPreviousGame) {
}
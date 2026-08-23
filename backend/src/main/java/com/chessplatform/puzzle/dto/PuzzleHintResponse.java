package com.chessplatform.puzzle.dto;

/** originSquare: la casilla de origen de la jugada correcta para el paso pedido — no revela el destino, solo qué pieza hay que mover. */
public record PuzzleHintResponse(String originSquare) {
}
package com.chessplatform.engine;

/**
 * Pieza inmutable: color + tipo. La posición NO vive aquí, vive en el tablero (mailbox).
 */
public record Piece(Color color, PieceType type) {
}

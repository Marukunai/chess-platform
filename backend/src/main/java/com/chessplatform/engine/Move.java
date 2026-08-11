package com.chessplatform.engine;

/**
 * Representa una jugada. promotionType solo se usa en coronaciones.
 */
public record Move(Square from, Square to, PieceType promotionType) {

    public Move(Square from, Square to) {
        this(from, to, null);
    }

    public boolean isPromotion() {
        return promotionType != null;
    }
}

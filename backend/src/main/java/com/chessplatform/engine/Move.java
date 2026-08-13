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

    /**
     * Notación UCI ("e2e4", o "e7e8q" con coronación) — el mismo formato que usa el
     * protocolo UCI para hablar con Stockfish (Fase 2), así que esta conversión ya queda
     * lista para cuando llegue ese momento. También es lo que se envía como
     * legalMovesUci en GameStateSyncMessage.
     */
    public String toUci() {
        String base = from.toAlgebraic() + to.toAlgebraic();
        return isPromotion() ? base + promotionLetter(promotionType) : base;
    }

    /**
     * Parsea una jugada en notación UCI ("e2e4" o "e7e8q"). Usado al recibir jugadas de
     * un motor externo vía UCI (Fase 2) — la capa de red propia (WebSocket) usa
     * MoveMessage.toEngineMove() en vez de esto, con from/to/promotionType como campos
     * JSON separados en lugar de una cadena empaquetada.
     */
    public static Move fromUci(String uci) {
        if (uci == null || (uci.length() != 4 && uci.length() != 5)) {
            throw new IllegalArgumentException("Notación UCI inválida: " + uci);
        }
        Square from = Square.fromAlgebraic(uci.substring(0, 2));
        Square to = Square.fromAlgebraic(uci.substring(2, 4));
        if (uci.length() == 5) {
            return new Move(from, to, parsePromotionLetter(uci.charAt(4)));
        }
        return new Move(from, to);
    }

    private static char promotionLetter(PieceType type) {
        return switch (type) {
            case QUEEN -> 'q';
            case ROOK -> 'r';
            case BISHOP -> 'b';
            case KNIGHT -> 'n';
            default -> throw new IllegalStateException("Tipo de pieza no válido para coronación: " + type);
        };
    }

    private static PieceType parsePromotionLetter(char letter) {
        return switch (Character.toLowerCase(letter)) {
            case 'q' -> PieceType.QUEEN;
            case 'r' -> PieceType.ROOK;
            case 'b' -> PieceType.BISHOP;
            case 'n' -> PieceType.KNIGHT;
            default -> throw new IllegalArgumentException("Letra de coronación no válida: " + letter);
        };
    }
}
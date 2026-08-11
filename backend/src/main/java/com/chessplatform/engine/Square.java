package com.chessplatform.engine;

/**
 * Casilla del tablero representada como índice 0-63 (mailbox 1D).
 * 0 = a1, 7 = h1, 56 = a8, 63 = h8 (fila*8 + columna, fila 0 = primera fila de blancas).
 */
public record Square(int index) {

    public Square {
        if (index < 0 || index > 63) {
            throw new IllegalArgumentException("Índice de casilla fuera de rango: " + index);
        }
    }

    public static Square of(int file, int rank) {
        if (file < 0 || file > 7 || rank < 0 || rank > 7) {
            throw new IllegalArgumentException(
                    "Coordenadas fuera del tablero: file=%d rank=%d".formatted(file, rank));
        }
        return new Square(rank * 8 + file);
    }

    public static Square fromAlgebraic(String algebraic) {
        // TODO (Fase 1): parsear notación algebraica ("e4", etc.)
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    public int file() {
        return index % 8;
    }

    public int rank() {
        return index / 8;
    }

    public String toAlgebraic() {
        char fileChar = (char) ('a' + file());
        int rankNum = rank() + 1;
        return "" + fileChar + rankNum;
    }
}

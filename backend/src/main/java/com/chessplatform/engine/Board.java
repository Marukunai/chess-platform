package com.chessplatform.engine;

import java.util.List;

/**
 * Tablero de ajedrez, representación mailbox 1D (64 casillas).
 *
 * Módulo puro: sin dependencias de Spring, JPA ni WebSocket — testeable con JUnit puro
 * y reutilizable tal cual desde el generador de puzzles (Fase 2). Ver ADR-001 y ADR-006
 * en docs/architecture-decisions.md.
 */
public class Board {

    private final Piece[] squares = new Piece[64];
    private Color turn = Color.WHITE;
    private boolean whiteCanCastleKingside = true;
    private boolean whiteCanCastleQueenside = true;
    private boolean blackCanCastleKingside = true;
    private boolean blackCanCastleQueenside = true;
    private Square enPassantTarget;
    private int halfmoveClock = 0;
    private int fullmoveNumber = 1;

    private Board() {
    }

    public static Board initial() {
        Board board = new Board();
        board.setupStartingPosition();
        return board;
    }

    private void setupStartingPosition() {
        PieceType[] backRank = {
                PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
                PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
        };
        for (int file = 0; file < 8; file++) {
            squares[Square.of(file, 0).index()] = new Piece(Color.WHITE, backRank[file]);
            squares[Square.of(file, 1).index()] = new Piece(Color.WHITE, PieceType.PAWN);
            squares[Square.of(file, 6).index()] = new Piece(Color.BLACK, PieceType.PAWN);
            squares[Square.of(file, 7).index()] = new Piece(Color.BLACK, backRank[file]);
        }
    }

    public Piece pieceAt(Square square) {
        return squares[square.index()];
    }

    public Color turn() {
        return turn;
    }

    /**
     * Genera los movimientos legales para la posición actual.
     *
     * TODO (Fase 1): implementar generación por tipo de pieza + filtrado de jugadas que
     * dejarían al propio rey en jaque. De momento devuelve lista vacía — placeholder para
     * poder arrancar el resto del sistema (WebSocket, persistencia) antes de cerrar el
     * motor de reglas por completo.
     */
    public List<Move> legalMoves() {
        return List.of();
    }

    /**
     * Aplica una jugada al tablero. Asume que ya ha sido validada como legal.
     *
     * TODO (Fase 1): mover la pieza + casos especiales (enroque, en passant, coronación) +
     * actualizar halfmoveClock/fullmoveNumber/turn/enPassantTarget/derechos de enroque.
     */
    public void applyMove(Move move) {
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    public boolean isInCheck(Color color) {
        // TODO (Fase 1): detectar si el rey de `color` está atacado
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    public boolean isCheckmate() {
        // TODO (Fase 1): jaque + sin movimientos legales
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    public boolean isStalemate() {
        // TODO (Fase 1): sin jaque + sin movimientos legales
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            for (int file = 0; file < 8; file++) {
                Piece p = squares[Square.of(file, rank).index()];
                sb.append(p == null ? '.' : pieceChar(p));
                sb.append(' ');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private char pieceChar(Piece p) {
        char c = switch (p.type()) {
            case PAWN -> 'p';
            case KNIGHT -> 'n';
            case BISHOP -> 'b';
            case ROOK -> 'r';
            case QUEEN -> 'q';
            case KING -> 'k';
        };
        return p.color() == Color.WHITE ? Character.toUpperCase(c) : c;
    }
}

package com.chessplatform.engine;

import java.util.ArrayList;
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

    /**
     * Tablero vacío, sin ninguna pieza colocada. Pensado para tests (construir posiciones
     * arbitrarias sin pasar por setupStartingPosition) y para el generador de puzzles en
     * Fase 2 (reconstruir posiciones intermedias de una partida ya jugada).
     */
    public static Board empty() {
        return new Board();
    }

    /**
     * Coloca una pieza directamente en una casilla, sin pasar por applyMove ni validar
     * legalidad. Uso exclusivo para tests y reconstrucción de posiciones — el flujo normal
     * de una partida siempre pasa por applyMove().
     */
    public void placePiece(Square square, Piece piece) {
        squares[square.index()] = piece;
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

    // Desplazamientos (file, rank) de las 8 posibles jugadas de un caballo.
    private static final int[] KNIGHT_FILE_OFFSETS = {1, 2, 2, 1, -1, -2, -2, -1};
    private static final int[] KNIGHT_RANK_OFFSETS = {2, 1, -1, -2, -2, -1, 1, 2};

    /**
     * Genera los movimientos legales para la posición actual.
     *
     * TODO (Fase 1): esto genera movimientos PSEUDO-legales — de momento solo caballo, el
     * resto de tipos de pieza siguen pendientes (ver switch más abajo). Además, todavía no
     * se filtran jugadas que dejarían al propio rey en jaque: eso llega en cuanto
     * isInCheck() esté implementado, que a su vez necesita poder generar los ataques de
     * todas las piezas, no solo sus movimientos legales (un peón ataca en diagonal aunque
     * no pueda "mover" en diagonal salvo captura).
     */
    public List<Move> legalMoves() {
        List<Move> moves = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            Piece piece = squares[i];
            if (piece == null || piece.color() != turn) {
                continue;
            }
            Square from = new Square(i);
            switch (piece.type()) {
                case KNIGHT -> moves.addAll(generateKnightMoves(from));
                // TODO: PAWN, BISHOP, ROOK, QUEEN, KING
                default -> {
                }
            }
        }
        return moves;
    }

    private List<Move> generateKnightMoves(Square from) {
        List<Move> moves = new ArrayList<>();
        Piece knight = pieceAt(from);

        for (int i = 0; i < KNIGHT_FILE_OFFSETS.length; i++) {
            int targetFile = from.file() + KNIGHT_FILE_OFFSETS[i];
            int targetRank = from.rank() + KNIGHT_RANK_OFFSETS[i];
            if (targetFile < 0 || targetFile > 7 || targetRank < 0 || targetRank > 7) {
                continue; // fuera del tablero
            }

            Square to = Square.of(targetFile, targetRank);
            Piece occupant = pieceAt(to);
            if (occupant == null || occupant.color() != knight.color()) {
                moves.add(new Move(from, to));
            }
            // Si occupant es del mismo color, no se añade — no se puede capturar pieza propia.
        }

        return moves;
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
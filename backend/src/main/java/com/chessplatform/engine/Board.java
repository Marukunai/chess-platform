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

    // Direcciones (file, rank) por rayo para piezas deslizantes.
    private static final int[][] ROOK_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int[][] BISHOP_DIRECTIONS = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] QUEEN_DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    /**
     * Genera los movimientos legales para la posición actual.
     *
     * TODO (Fase 1): esto genera movimientos PSEUDO-legales — caballo, piezas deslizantes
     * (torre/alfil/dama) y rey ya implementados, queda pendiente el peón (ver switch más
     * abajo). Además, todavía no se filtran jugadas que dejarían al propio rey en jaque
     * (esto incluye que el rey "pueda" moverse a una casilla atacada, lo cual es ilegal en
     * ajedrez real): eso llega en cuanto isInCheck() esté implementado, que a su vez
     * necesita poder generar los ataques de todas las piezas, no solo sus movimientos
     * legales (un peón ataca en diagonal aunque no pueda "mover" en diagonal salvo
     * captura).
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
                case ROOK -> moves.addAll(generateSlidingMoves(from, ROOK_DIRECTIONS));
                case BISHOP -> moves.addAll(generateSlidingMoves(from, BISHOP_DIRECTIONS));
                case QUEEN -> moves.addAll(generateSlidingMoves(from, QUEEN_DIRECTIONS));
                case KING -> moves.addAll(generateKingMoves(from));
                // TODO: PAWN
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
     * Generación compartida para torre/alfil/dama: lanza un rayo por cada dirección hasta
     * salirse del tablero, chocar con una pieza propia (rayo termina, sin incluir esa
     * casilla) o chocar con una pieza enemiga (rayo termina, incluyendo esa casilla como
     * captura).
     */
    private List<Move> generateSlidingMoves(Square from, int[][] directions) {
        List<Move> moves = new ArrayList<>();
        Piece piece = pieceAt(from);

        for (int[] direction : directions) {
            int file = from.file();
            int rank = from.rank();

            while (true) {
                file += direction[0];
                rank += direction[1];
                if (file < 0 || file > 7 || rank < 0 || rank > 7) {
                    break; // fuera del tablero, fin del rayo
                }

                Square to = Square.of(file, rank);
                Piece occupant = pieceAt(to);

                if (occupant == null) {
                    moves.add(new Move(from, to));
                    continue; // casilla vacía, el rayo continúa
                }

                if (occupant.color() != piece.color()) {
                    moves.add(new Move(from, to)); // captura válida
                }
                break; // pieza propia o enemiga: el rayo no puede seguir más allá
            }
        }

        return moves;
    }

    private List<Move> generateKingMoves(Square from) {
        List<Move> moves = new ArrayList<>();
        Piece king = pieceAt(from);

        // Reutiliza las 8 direcciones de la dama, pero se detiene tras un solo paso —
        // el rey no desliza.
        for (int[] direction : QUEEN_DIRECTIONS) {
            int targetFile = from.file() + direction[0];
            int targetRank = from.rank() + direction[1];
            if (targetFile < 0 || targetFile > 7 || targetRank < 0 || targetRank > 7) {
                continue; // fuera del tablero
            }

            Square to = Square.of(targetFile, targetRank);
            Piece occupant = pieceAt(to);
            if (occupant == null || occupant.color() != king.color()) {
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
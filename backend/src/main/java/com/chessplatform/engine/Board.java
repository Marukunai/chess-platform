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

    /**
     * Fija manualmente el turno activo. Uso exclusivo para tests — en el flujo normal de
     * una partida el turno lo alterna applyMove() tras cada jugada.
     */
    public void setTurn(Color color) {
        this.turn = color;
    }

    /**
     * Fija manualmente la casilla objetivo de captura al paso. Uso exclusivo para tests —
     * en el flujo normal de una partida la fija applyMove() tras un doble paso de peón.
     */
    public void setEnPassantTarget(Square square) {
        this.enPassantTarget = square;
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
     * Con esto las 6 piezas ya generan movimientos PSEUDO-legales, incluyendo las reglas
     * especiales del peón (doble paso inicial, captura al paso, coronación). Quedan dos
     * cosas pendientes para Fase 1:
     *  1. Enroque — se añade junto con el resto de casos especiales.
     *  2. Filtrar jugadas que dejarían al propio rey en jaque (incluye que el rey "pueda"
     *     moverse a una casilla atacada) — isInCheck() ya está implementado (ver más
     *     abajo), pero legalMoves() todavía no lo usa; ese filtrado es el siguiente paso.
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
                case PAWN -> moves.addAll(generatePawnMoves(from));
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

    private static final PieceType[] PROMOTION_TYPES = {
            PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT
    };

    private List<Move> generatePawnMoves(Square from) {
        List<Move> moves = new ArrayList<>();
        Piece pawn = pieceAt(from);
        boolean isWhite = pawn.color() == Color.WHITE;

        int direction = isWhite ? 1 : -1;
        int startingRank = isWhite ? 1 : 6;
        int promotionRank = isWhite ? 7 : 0;

        int file = from.file();
        int rank = from.rank();

        // Empuje simple + doble paso inicial. El doble paso solo es posible si el peón
        // sigue en su fila de partida Y la casilla intermedia (el propio empuje simple)
        // también está libre — no se puede "saltar" sobre una pieza.
        int oneStepRank = rank + direction;
        if (oneStepRank >= 0 && oneStepRank <= 7 && pieceAt(Square.of(file, oneStepRank)) == null) {
            addPawnMove(moves, from, Square.of(file, oneStepRank), promotionRank);

            int twoStepRank = rank + 2 * direction;
            if (rank == startingRank && pieceAt(Square.of(file, twoStepRank)) == null) {
                moves.add(new Move(from, Square.of(file, twoStepRank)));
            }
        }

        // Capturas diagonales, incluyendo captura al paso: el peón puede mover en
        // diagonal si hay una pieza enemiga en esa casilla, o si esa casilla es
        // enPassantTarget (el peón enemigo que hizo doble paso "queda atrás", no en la
        // propia casilla destino — así se representa la captura al paso en UCI/FEN).
        for (int fileOffset : new int[]{-1, 1}) {
            int targetFile = file + fileOffset;
            int targetRank = rank + direction;
            if (targetFile < 0 || targetFile > 7 || targetRank < 0 || targetRank > 7) {
                continue;
            }

            Square to = Square.of(targetFile, targetRank);
            Piece occupant = pieceAt(to);
            boolean isEnPassantCapture = to.equals(enPassantTarget);

            if ((occupant != null && occupant.color() != pawn.color()) || isEnPassantCapture) {
                addPawnMove(moves, from, to, promotionRank);
            }
        }

        return moves;
    }

    /**
     * Añade una jugada de peón, expandiéndola a las 4 posibles coronaciones
     * (dama/torre/alfil/caballo) si la casilla destino es la última fila.
     */
    private void addPawnMove(List<Move> moves, Square from, Square to, int promotionRank) {
        if (to.rank() == promotionRank) {
            for (PieceType promotionType : PROMOTION_TYPES) {
                moves.add(new Move(from, to, promotionType));
            }
        } else {
            moves.add(new Move(from, to));
        }
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
        Square kingSquare = findKing(color);
        if (kingSquare == null) {
            throw new IllegalStateException("No hay rey de color " + color + " en el tablero");
        }
        return isSquareAttacked(kingSquare, color.opposite());
    }

    private Square findKing(Color color) {
        for (int i = 0; i < 64; i++) {
            Piece piece = squares[i];
            if (piece != null && piece.color() == color && piece.type() == PieceType.KING) {
                return new Square(i);
            }
        }
        return null;
    }

    /**
     * ¿Está `square` amenazada por alguna pieza de `byColor`? A diferencia de la
     * generación de movimientos, esto no comprueba si una pieza "puede moverse" a
     * `square` — un peón amenaza en diagonal exista o no algo que capturar ahí, cosa que
     * no ocurre con su movimiento normal. Público porque el enroque (Fase 1, casos
     * especiales) también lo va a necesitar: no se puede enrocar a través de una casilla
     * atacada.
     */
    public boolean isSquareAttacked(Square square, Color byColor) {
        return isAttackedByPawn(square, byColor)
                || isAttackedByKnight(square, byColor)
                || isAttackedByKing(square, byColor)
                || isAttackedBySlidingPiece(square, byColor, ROOK_DIRECTIONS, PieceType.ROOK, PieceType.QUEEN)
                || isAttackedBySlidingPiece(square, byColor, BISHOP_DIRECTIONS, PieceType.BISHOP, PieceType.QUEEN);
    }

    private boolean isAttackedByPawn(Square square, Color byColor) {
        // Un peón de byColor ataca en diagonal hacia delante (en su propia dirección de
        // avance). Buscamos "hacia atrás" desde `square`: si hay un peón de byColor una
        // fila detrás (respecto a su dirección de ataque) y una columna a cualquier lado,
        // esa pieza ataca `square`.
        int attackDirection = byColor == Color.WHITE ? 1 : -1;
        int sourceRank = square.rank() - attackDirection;
        if (sourceRank < 0 || sourceRank > 7) {
            return false;
        }

        for (int fileOffset : new int[]{-1, 1}) {
            int sourceFile = square.file() + fileOffset;
            if (sourceFile < 0 || sourceFile > 7) {
                continue;
            }
            Piece occupant = pieceAt(Square.of(sourceFile, sourceRank));
            if (occupant != null && occupant.color() == byColor && occupant.type() == PieceType.PAWN) {
                return true;
            }
        }
        return false;
    }

    private boolean isAttackedByKnight(Square square, Color byColor) {
        for (int i = 0; i < KNIGHT_FILE_OFFSETS.length; i++) {
            int targetFile = square.file() + KNIGHT_FILE_OFFSETS[i];
            int targetRank = square.rank() + KNIGHT_RANK_OFFSETS[i];
            if (targetFile < 0 || targetFile > 7 || targetRank < 0 || targetRank > 7) {
                continue;
            }
            Piece occupant = pieceAt(Square.of(targetFile, targetRank));
            if (occupant != null && occupant.color() == byColor && occupant.type() == PieceType.KNIGHT) {
                return true;
            }
        }
        return false;
    }

    private boolean isAttackedByKing(Square square, Color byColor) {
        for (int[] direction : QUEEN_DIRECTIONS) {
            int targetFile = square.file() + direction[0];
            int targetRank = square.rank() + direction[1];
            if (targetFile < 0 || targetFile > 7 || targetRank < 0 || targetRank > 7) {
                continue;
            }
            Piece occupant = pieceAt(Square.of(targetFile, targetRank));
            if (occupant != null && occupant.color() == byColor && occupant.type() == PieceType.KING) {
                return true;
            }
        }
        return false;
    }

    /**
     * Lanza un rayo por cada dirección desde `square`. La primera pieza que encuentra en
     * cada rayo decide: si es de byColor y su tipo está en matchingTypes, hay ataque. Si
     * es cualquier otra pieza (propia, enemiga de otro tipo), bloquea el rayo — no importa
     * si el "verdadero" atacante está más allá, no puede atacar a través de una pieza.
     */
    private boolean isAttackedBySlidingPiece(Square square, Color byColor, int[][] directions,
                                             PieceType... matchingTypes) {
        for (int[] direction : directions) {
            int file = square.file();
            int rank = square.rank();

            while (true) {
                file += direction[0];
                rank += direction[1];
                if (file < 0 || file > 7 || rank < 0 || rank > 7) {
                    break;
                }

                Piece occupant = pieceAt(Square.of(file, rank));
                if (occupant == null) {
                    continue; // casilla vacía, el rayo sigue
                }

                if (occupant.color() == byColor && matchesAny(occupant.type(), matchingTypes)) {
                    return true;
                }
                break; // cualquier pieza bloquea el rayo, sea o no la que buscamos
            }
        }
        return false;
    }

    private boolean matchesAny(PieceType type, PieceType[] candidates) {
        for (PieceType candidate : candidates) {
            if (candidate == type) {
                return true;
            }
        }
        return false;
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
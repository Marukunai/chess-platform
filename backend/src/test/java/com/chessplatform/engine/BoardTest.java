package com.chessplatform.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BoardTest {

    @Test
    void initialBoardHasWhiteToMove() {
        Board board = Board.initial();
        assertThat(board.turn()).isEqualTo(Color.WHITE);
    }

    @Test
    void initialBoardHasCorrectBackRank() {
        Board board = Board.initial();
        assertThat(board.pieceAt(Square.of(0, 0))).isEqualTo(new Piece(Color.WHITE, PieceType.ROOK));
        assertThat(board.pieceAt(Square.of(4, 0))).isEqualTo(new Piece(Color.WHITE, PieceType.KING));
        assertThat(board.pieceAt(Square.of(4, 7))).isEqualTo(new Piece(Color.BLACK, PieceType.KING));
    }

    @Test
    void squareConvertsToAlgebraicNotation() {
        assertThat(Square.of(0, 0).toAlgebraic()).isEqualTo("a1");
        assertThat(Square.of(7, 7).toAlgebraic()).isEqualTo("h8");
        assertThat(Square.of(4, 3).toAlgebraic()).isEqualTo("e4");
    }

    @Test
    void knightInCenterOfEmptyBoardHasEightMoves() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 4), new Piece(Color.WHITE, PieceType.KNIGHT)); // e5

        assertThat(board.legalMoves()).hasSize(8);
    }

    @Test
    void knightInCornerOfEmptyBoardHasOnlyTwoMoves() {
        Board board = Board.empty();
        board.placePiece(Square.of(0, 0), new Piece(Color.WHITE, PieceType.KNIGHT)); // a1

        List<Move> moves = board.legalMoves();
        assertThat(moves).hasSize(2);
        assertThat(moves).extracting(Move::to)
                .containsExactlyInAnyOrder(Square.of(1, 2), Square.of(2, 1)); // b3, c2
    }

    @Test
    void knightCannotCaptureOwnPiece() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 4), new Piece(Color.WHITE, PieceType.KNIGHT)); // e5
        board.placePiece(Square.of(6, 5), new Piece(Color.WHITE, PieceType.PAWN)); // g6, uno de los 8 destinos

        assertThat(board.legalMoves()).hasSize(7);
    }

    @Test
    void knightCanCaptureOpponentPiece() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 4), new Piece(Color.WHITE, PieceType.KNIGHT)); // e5
        board.placePiece(Square.of(6, 5), new Piece(Color.BLACK, PieceType.PAWN)); // g6

        List<Move> moves = board.legalMoves();
        assertThat(moves).hasSize(8); // sigue teniendo 8 destinos, uno de ellos es captura
        assertThat(moves).extracting(Move::to).contains(Square.of(6, 5));
    }

    @Test
    void bothKnightsOnInitialBoardHaveTwoMovesEachBeforeOtherPiecesAreImplemented() {
        // Nota: en la posición inicial cada caballo (b1, g1) solo tiene 2 movimientos
        // legales reales de todas formas (a3/c3 y f3/h3), así que este test no cambiará
        // de resultado cuando se implementen el resto de piezas — pero de momento sirve
        // para confirmar que legalMoves() recorre correctamente ambos caballos blancos
        // a la vez y no solo uno.
        Board board = Board.initial();

        long whiteKnightMoves = board.legalMoves().stream()
                .filter(m -> m.from().equals(Square.of(1, 0)) || m.from().equals(Square.of(6, 0)))
                .count();

        assertThat(whiteKnightMoves).isEqualTo(4);
    }
}
package com.chessplatform.engine;

import org.junit.jupiter.api.Test;

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
}

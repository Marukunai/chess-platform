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
        Square knightSquare = Square.of(4, 4); // e5
        board.placePiece(knightSquare, new Piece(Color.WHITE, PieceType.KNIGHT));
        board.placePiece(Square.of(6, 5), new Piece(Color.WHITE, PieceType.PAWN)); // g6, uno de los 8 destinos

        // Filtramos por la casilla de origen del caballo: la pieza "decoy" colocada para
        // bloquear una de sus capturas es un peón, y ahora que el peón también genera sus
        // propios movimientos, contaminaría el recuento si no aislamos los del caballo.
        List<Move> knightMoves = board.legalMoves().stream()
                .filter(m -> m.from().equals(knightSquare))
                .toList();

        assertThat(knightMoves).hasSize(7);
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

    @Test
    void rookInCenterOfEmptyBoardHasFourteenMoves() {
        // Una torre en un tablero vacío siempre ve 7 casillas en horizontal + 7 en
        // vertical, sea cual sea la casilla de partida (a diferencia del alfil, no
        // depende de estar cerca de un borde).
        Board board = Board.empty();
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.ROOK)); // d4

        assertThat(board.legalMoves()).hasSize(14);
    }

    @Test
    void rookStopsAtOwnPieceAndDoesNotCaptureIt() {
        Board board = Board.empty();
        Square rookSquare = Square.of(3, 3); // d4
        board.placePiece(rookSquare, new Piece(Color.WHITE, PieceType.ROOK));
        board.placePiece(Square.of(3, 5), new Piece(Color.WHITE, PieceType.PAWN)); // d6, bloquea el rayo vertical

        // Filtramos por la casilla de origen de la torre: el peón bloqueador también
        // genera su propio movimiento (d6-d7), que si no se aísla contamina el "to" que
        // estamos comprobando aquí.
        List<Move> rookMoves = board.legalMoves().stream()
                .filter(m -> m.from().equals(rookSquare))
                .toList();

        // Vertical hacia arriba: solo d5 (1 casilla, no llega a d6 ni más allá).
        assertThat(rookMoves).extracting(Move::to).doesNotContain(Square.of(3, 5), Square.of(3, 6), Square.of(3, 7));
        assertThat(rookMoves).extracting(Move::to).contains(Square.of(3, 4)); // d5 sigue siendo válida
    }

    @Test
    void rookCanCaptureEnemyPieceButNotPassThroughIt() {
        Board board = Board.empty();
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.ROOK)); // d4
        board.placePiece(Square.of(3, 5), new Piece(Color.BLACK, PieceType.PAWN)); // d6

        List<Move> moves = board.legalMoves();
        assertThat(moves).extracting(Move::to).contains(Square.of(3, 5)); // captura en d6 sí es válida
        assertThat(moves).extracting(Move::to).doesNotContain(Square.of(3, 6), Square.of(3, 7)); // pero no más allá
    }

    @Test
    void bishopInCenterOfEmptyBoardHasThirteenMoves() {
        Board board = Board.empty();
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.BISHOP)); // d4

        assertThat(board.legalMoves()).hasSize(13);
    }

    @Test
    void bishopInCornerOfEmptyBoardHasOnlySevenMoves() {
        // Desde a1 solo hay una diagonal posible (hacia h8), las otras tres se salen del
        // tablero inmediatamente.
        Board board = Board.empty();
        board.placePiece(Square.of(0, 0), new Piece(Color.WHITE, PieceType.BISHOP)); // a1

        assertThat(board.legalMoves()).hasSize(7);
    }

    @Test
    void queenInCenterOfEmptyBoardCombinesRookAndBishopMoves() {
        Board board = Board.empty();
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.QUEEN)); // d4

        assertThat(board.legalMoves()).hasSize(14 + 13); // torre + alfil desde la misma casilla
    }

    @Test
    void kingInCenterOfEmptyBoardHasEightMoves() {
        Board board = Board.empty();
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.KING)); // d4

        assertThat(board.legalMoves()).hasSize(8);
    }

    @Test
    void kingInCornerOfEmptyBoardHasOnlyThreeMoves() {
        Board board = Board.empty();
        board.placePiece(Square.of(0, 0), new Piece(Color.WHITE, PieceType.KING)); // a1

        List<Move> moves = board.legalMoves();
        assertThat(moves).hasSize(3);
        assertThat(moves).extracting(Move::to)
                .containsExactlyInAnyOrder(Square.of(1, 0), Square.of(0, 1), Square.of(1, 1)); // b1, a2, b2
    }

    @Test
    void kingCannotCaptureOwnPiece() {
        Board board = Board.empty();
        Square kingSquare = Square.of(3, 3); // d4
        board.placePiece(kingSquare, new Piece(Color.WHITE, PieceType.KING));
        board.placePiece(Square.of(3, 4), new Piece(Color.WHITE, PieceType.PAWN)); // d5, adyacente

        // Igual que en el caballo: aislamos los movimientos del rey, porque el peón
        // bloqueador ahora también genera el suyo propio (d5-d6).
        List<Move> kingMoves = board.legalMoves().stream()
                .filter(m -> m.from().equals(kingSquare))
                .toList();

        assertThat(kingMoves).hasSize(7);
    }

    @Test
    void kingCanCaptureAdjacentEnemyPiece() {
        Board board = Board.empty();
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.KING)); // d4
        board.placePiece(Square.of(3, 4), new Piece(Color.BLACK, PieceType.PAWN)); // d5

        List<Move> moves = board.legalMoves();
        assertThat(moves).hasSize(8); // sigue teniendo 8 destinos, uno de ellos es captura
        assertThat(moves).extracting(Move::to).contains(Square.of(3, 4));
    }

    @Test
    void kingOnInitialBoardHasNoMovesSurroundedByOwnPieces() {
        // Caso límite distinto al de bloqueo parcial: aquí las 8 direcciones están
        // ocupadas por piezas propias a la vez.
        Board board = Board.initial();

        long whiteKingMoves = board.legalMoves().stream()
                .filter(m -> m.from().equals(Square.of(4, 0)))
                .count();

        assertThat(whiteKingMoves).isZero();
    }

    @Test
    void whitePawnOnStartingRankHasSingleAndDoublePush() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 1), new Piece(Color.WHITE, PieceType.PAWN)); // e2

        List<Move> moves = board.legalMoves();
        assertThat(moves).hasSize(2);
        assertThat(moves).extracting(Move::to)
                .containsExactlyInAnyOrder(Square.of(4, 2), Square.of(4, 3)); // e3, e4
    }

    @Test
    void whitePawnNotOnStartingRankHasOnlySinglePush() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 2), new Piece(Color.WHITE, PieceType.PAWN)); // e3

        List<Move> moves = board.legalMoves();
        assertThat(moves).hasSize(1);
        assertThat(moves.get(0).to()).isEqualTo(Square.of(4, 3)); // e4
    }

    @Test
    void whitePawnBlockedDirectlyAheadCannotPushAtAll() {
        // Un peón nunca captura en línea recta, así que una pieza justo delante lo
        // bloquea por completo — a diferencia de una torre, no puede "capturarla".
        Board board = Board.empty();
        board.placePiece(Square.of(4, 1), new Piece(Color.WHITE, PieceType.PAWN)); // e2
        board.placePiece(Square.of(4, 2), new Piece(Color.BLACK, PieceType.KNIGHT)); // e3

        assertThat(board.legalMoves()).isEmpty();
    }

    @Test
    void whitePawnDoublePushBlockedWhenTargetOccupiedButSingleStepStillAllowed() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 1), new Piece(Color.WHITE, PieceType.PAWN)); // e2
        board.placePiece(Square.of(4, 3), new Piece(Color.BLACK, PieceType.KNIGHT)); // e4, dos casillas por delante

        List<Move> moves = board.legalMoves();
        assertThat(moves).hasSize(1);
        assertThat(moves.get(0).to()).isEqualTo(Square.of(4, 2)); // e3, el doble paso no es posible
    }

    @Test
    void whitePawnCapturesDiagonallyOnBothSides() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 3), new Piece(Color.WHITE, PieceType.PAWN)); // e4
        board.placePiece(Square.of(3, 4), new Piece(Color.BLACK, PieceType.PAWN)); // d5
        board.placePiece(Square.of(5, 4), new Piece(Color.BLACK, PieceType.PAWN)); // f5

        List<Move> moves = board.legalMoves();
        assertThat(moves).hasSize(3); // empuje simple + 2 capturas
        assertThat(moves).extracting(Move::to)
                .containsExactlyInAnyOrder(Square.of(4, 4), Square.of(3, 4), Square.of(5, 4));
    }

    @Test
    void whitePawnCannotCaptureOwnPieceDiagonally() {
        Board board = Board.empty();
        Square pawnSquare = Square.of(4, 3); // e4
        board.placePiece(pawnSquare, new Piece(Color.WHITE, PieceType.PAWN));
        board.placePiece(Square.of(3, 4), new Piece(Color.WHITE, PieceType.PAWN)); // d5
        board.placePiece(Square.of(5, 4), new Piece(Color.WHITE, PieceType.PAWN)); // f5

        // Los dos peones "propios" colocados en diagonal ahora también generan sus
        // propios empujes (d5-d6, f5-f6) — aislamos por casilla de origen para comprobar
        // solo los movimientos del peón bajo test.
        List<Move> pawnMoves = board.legalMoves().stream()
                .filter(m -> m.from().equals(pawnSquare))
                .toList();

        assertThat(pawnMoves).hasSize(1); // solo el empuje simple, ninguna captura
        assertThat(pawnMoves.get(0).to()).isEqualTo(Square.of(4, 4));
    }

    @Test
    void whitePawnCanCaptureEnPassant() {
        // Simula la posición justo después de que negras hayan hecho doble paso d7-d5:
        // el peón blanco en e5 puede capturarlo "al paso", aterrizando en d6 (casilla
        // vacía) en vez de en d5 (donde de hecho está el peón negro capturado).
        Board board = Board.empty();
        board.placePiece(Square.of(4, 4), new Piece(Color.WHITE, PieceType.PAWN)); // e5
        board.placePiece(Square.of(3, 4), new Piece(Color.BLACK, PieceType.PAWN)); // d5
        board.setEnPassantTarget(Square.of(3, 5)); // d6

        List<Move> moves = board.legalMoves();
        assertThat(moves).hasSize(2); // empuje simple a e6 + captura al paso a d6
        assertThat(moves).extracting(Move::to)
                .containsExactlyInAnyOrder(Square.of(4, 5), Square.of(3, 5));
    }

    @Test
    void whitePawnPromotesToAllFourPiecesOnPush() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 6), new Piece(Color.WHITE, PieceType.PAWN)); // e7

        List<Move> moves = board.legalMoves();
        assertThat(moves).hasSize(4);
        assertThat(moves).allMatch(m -> m.to().equals(Square.of(4, 7))); // e8
        assertThat(moves).extracting(Move::promotionType)
                .containsExactlyInAnyOrder(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT);
    }

    @Test
    void whitePawnPromotesToAllFourPiecesOnCapture() {
        Board board = Board.empty();
        Square pawnSquare = Square.of(4, 6); // e7
        board.placePiece(pawnSquare, new Piece(Color.WHITE, PieceType.PAWN));
        board.placePiece(Square.of(4, 7), new Piece(Color.WHITE, PieceType.ROOK)); // e8, bloquea el empuje recto
        board.placePiece(Square.of(5, 7), new Piece(Color.BLACK, PieceType.KNIGHT)); // f8, capturable en diagonal

        // La torre colocada para bloquear el empuje recto también genera sus propios
        // movimientos (torre ya implementada) — aislamos por casilla de origen del peón.
        List<Move> pawnMoves = board.legalMoves().stream()
                .filter(m -> m.from().equals(pawnSquare))
                .toList();

        assertThat(pawnMoves).hasSize(4); // solo la captura, el empuje recto está bloqueado
        assertThat(pawnMoves).allMatch(m -> m.to().equals(Square.of(5, 7)));
        assertThat(pawnMoves).extracting(Move::promotionType)
                .containsExactlyInAnyOrder(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT);
    }

    @Test
    void blackPawnMovesInTheOppositeDirection() {
        Board board = Board.empty();
        board.setTurn(Color.BLACK);
        board.placePiece(Square.of(4, 6), new Piece(Color.BLACK, PieceType.PAWN)); // e7

        List<Move> moves = board.legalMoves();
        assertThat(moves).hasSize(2);
        assertThat(moves).extracting(Move::to)
                .containsExactlyInAnyOrder(Square.of(4, 5), Square.of(4, 4)); // e6, e5
    }

    @Test
    void allWhitePawnsOnInitialBoardHaveTwoMovesEach() {
        Board board = Board.initial();

        long pawnMoves = board.legalMoves().stream()
                .filter(m -> m.from().rank() == 1)
                .count();

        assertThat(pawnMoves).isEqualTo(16); // 8 peones x 2 movimientos (empuje simple + doble)
    }
}
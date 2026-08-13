package com.chessplatform.engine;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThat(board.pseudoLegalMoves()).hasSize(8);
    }

    @Test
    void knightInCornerOfEmptyBoardHasOnlyTwoMoves() {
        Board board = Board.empty();
        board.placePiece(Square.of(0, 0), new Piece(Color.WHITE, PieceType.KNIGHT)); // a1

        List<Move> moves = board.pseudoLegalMoves();
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
        // Usamos pseudoLegalMoves() (no legalMoves()) porque esto prueba geometría pura,
        // no legalidad bajo jaque — no hay rey en este tablero.
        List<Move> knightMoves = board.pseudoLegalMoves().stream()
                .filter(m -> m.from().equals(knightSquare))
                .toList();

        assertThat(knightMoves).hasSize(7);
    }

    @Test
    void knightCanCaptureOpponentPiece() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 4), new Piece(Color.WHITE, PieceType.KNIGHT)); // e5
        board.placePiece(Square.of(6, 5), new Piece(Color.BLACK, PieceType.PAWN)); // g6

        List<Move> moves = board.pseudoLegalMoves();
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

        assertThat(board.pseudoLegalMoves()).hasSize(14);
    }

    @Test
    void rookStopsAtOwnPieceAndDoesNotCaptureIt() {
        Board board = Board.empty();
        Square rookSquare = Square.of(3, 3); // d4
        board.placePiece(rookSquare, new Piece(Color.WHITE, PieceType.ROOK));
        board.placePiece(Square.of(3, 5), new Piece(Color.WHITE, PieceType.PAWN)); // d6, bloquea el rayo vertical

        // Filtramos por la casilla de origen de la torre: el peón bloqueador también
        // genera su propio movimiento (d6-d7), que si no se aísla contamina el "to" que
        // estamos comprobando aquí. pseudoLegalMoves() porque no hay rey en este tablero.
        List<Move> rookMoves = board.pseudoLegalMoves().stream()
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

        List<Move> moves = board.pseudoLegalMoves();
        assertThat(moves).extracting(Move::to).contains(Square.of(3, 5)); // captura en d6 sí es válida
        assertThat(moves).extracting(Move::to).doesNotContain(Square.of(3, 6), Square.of(3, 7)); // pero no más allá
    }

    @Test
    void bishopInCenterOfEmptyBoardHasThirteenMoves() {
        Board board = Board.empty();
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.BISHOP)); // d4

        assertThat(board.pseudoLegalMoves()).hasSize(13);
    }

    @Test
    void bishopInCornerOfEmptyBoardHasOnlySevenMoves() {
        // Desde a1 solo hay una diagonal posible (hacia h8), las otras tres se salen del
        // tablero inmediatamente.
        Board board = Board.empty();
        board.placePiece(Square.of(0, 0), new Piece(Color.WHITE, PieceType.BISHOP)); // a1

        assertThat(board.pseudoLegalMoves()).hasSize(7);
    }

    @Test
    void queenInCenterOfEmptyBoardCombinesRookAndBishopMoves() {
        Board board = Board.empty();
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.QUEEN)); // d4

        assertThat(board.pseudoLegalMoves()).hasSize(14 + 13); // torre + alfil desde la misma casilla
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
        // pseudoLegalMoves(): esto prueba mecánica de captura pura, no legalidad bajo
        // jaque — no hay rey negro en este tablero, y el peón negro colocado aquí sí
        // ataca en diagonal (ver kingCannotMoveIntoSquareAttackedByEnemyPawn más abajo
        // para el test que sí comprueba ese filtrado con legalMoves()).
        Board board = Board.empty();
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.KING)); // d4
        board.placePiece(Square.of(3, 4), new Piece(Color.BLACK, PieceType.PAWN)); // d5

        List<Move> moves = board.pseudoLegalMoves();
        assertThat(moves).hasSize(8); // sigue teniendo 8 destinos, uno de ellos es captura
        assertThat(moves).extracting(Move::to).contains(Square.of(3, 4));
    }

    @Test
    void kingCannotMoveIntoSquareAttackedByEnemyPawn() {
        // El peón negro en d5 ataca en diagonal hacia c4 y e4 (no hacia delante en línea
        // recta) — el rey puede capturarlo, pero no puede "esquivarlo" pisando esas dos
        // casillas, que seguirían estando bajo ataque.
        Board board = Board.empty();
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.KING)); // d4
        board.placePiece(Square.of(3, 4), new Piece(Color.BLACK, PieceType.PAWN)); // d5

        List<Move> kingMoves = board.legalMoves();

        assertThat(kingMoves).hasSize(6); // 8 pseudo-legales menos c4 y e4
        assertThat(kingMoves).extracting(Move::to)
                .doesNotContain(Square.of(2, 3), Square.of(4, 3)); // c4, e4
        assertThat(kingMoves).extracting(Move::to).contains(Square.of(3, 4)); // d5 sigue siendo captura válida
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

        List<Move> moves = board.pseudoLegalMoves();
        assertThat(moves).hasSize(2);
        assertThat(moves).extracting(Move::to)
                .containsExactlyInAnyOrder(Square.of(4, 2), Square.of(4, 3)); // e3, e4
    }

    @Test
    void whitePawnNotOnStartingRankHasOnlySinglePush() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 2), new Piece(Color.WHITE, PieceType.PAWN)); // e3

        List<Move> moves = board.pseudoLegalMoves();
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

        assertThat(board.pseudoLegalMoves()).isEmpty();
    }

    @Test
    void whitePawnDoublePushBlockedWhenTargetOccupiedButSingleStepStillAllowed() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 1), new Piece(Color.WHITE, PieceType.PAWN)); // e2
        board.placePiece(Square.of(4, 3), new Piece(Color.BLACK, PieceType.KNIGHT)); // e4, dos casillas por delante

        List<Move> moves = board.pseudoLegalMoves();
        assertThat(moves).hasSize(1);
        assertThat(moves.get(0).to()).isEqualTo(Square.of(4, 2)); // e3, el doble paso no es posible
    }

    @Test
    void whitePawnCapturesDiagonallyOnBothSides() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 3), new Piece(Color.WHITE, PieceType.PAWN)); // e4
        board.placePiece(Square.of(3, 4), new Piece(Color.BLACK, PieceType.PAWN)); // d5
        board.placePiece(Square.of(5, 4), new Piece(Color.BLACK, PieceType.PAWN)); // f5

        List<Move> moves = board.pseudoLegalMoves();
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
        List<Move> pawnMoves = board.pseudoLegalMoves().stream()
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

        List<Move> moves = board.pseudoLegalMoves();
        assertThat(moves).hasSize(2); // empuje simple a e6 + captura al paso a d6
        assertThat(moves).extracting(Move::to)
                .containsExactlyInAnyOrder(Square.of(4, 5), Square.of(3, 5));
    }

    @Test
    void whitePawnPromotesToAllFourPiecesOnPush() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 6), new Piece(Color.WHITE, PieceType.PAWN)); // e7

        List<Move> moves = board.pseudoLegalMoves();
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
        // movimientos — aislamos por casilla de origen del peón.
        List<Move> pawnMoves = board.pseudoLegalMoves().stream()
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

        List<Move> moves = board.pseudoLegalMoves();
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

    @Test
    void kingNotInCheckWhenNoAttackersPresent() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 3), new Piece(Color.WHITE, PieceType.KING)); // e4

        assertThat(board.isInCheck(Color.WHITE)).isFalse();
    }

    @Test
    void kingInCheckFromRookAlongSameRank() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 0), new Piece(Color.WHITE, PieceType.KING)); // e1
        board.placePiece(Square.of(0, 0), new Piece(Color.BLACK, PieceType.ROOK)); // a1, misma fila

        assertThat(board.isInCheck(Color.WHITE)).isTrue();
    }

    @Test
    void kingInCheckFromBishopAlongDiagonal() {
        Board board = Board.empty();
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.KING)); // d4
        board.placePiece(Square.of(0, 0), new Piece(Color.BLACK, PieceType.BISHOP)); // a1, misma diagonal

        assertThat(board.isInCheck(Color.WHITE)).isTrue();
    }

    @Test
    void kingInCheckFromQueenAlongFile() {
        Board board = Board.empty();
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.KING)); // d4
        board.placePiece(Square.of(3, 7), new Piece(Color.BLACK, PieceType.QUEEN)); // d8, misma columna

        assertThat(board.isInCheck(Color.WHITE)).isTrue();
    }

    @Test
    void kingInCheckFromKnight() {
        Board board = Board.empty();
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.KING)); // d4
        board.placePiece(Square.of(1, 2), new Piece(Color.BLACK, PieceType.KNIGHT)); // b3

        assertThat(board.isInCheck(Color.WHITE)).isTrue();
    }

    @Test
    void whiteKingInCheckFromBlackPawnDiagonalAttack() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 0), new Piece(Color.WHITE, PieceType.KING)); // e1
        board.placePiece(Square.of(3, 1), new Piece(Color.BLACK, PieceType.PAWN)); // d2, ataca hacia rank menor

        assertThat(board.isInCheck(Color.WHITE)).isTrue();
    }

    @Test
    void blackKingInCheckFromWhitePawnDiagonalAttack() {
        // Confirma la asimetría de dirección: un peón blanco ataca hacia rank mayor,
        // justo lo contrario que el negro del test anterior.
        Board board = Board.empty();
        board.placePiece(Square.of(4, 4), new Piece(Color.BLACK, PieceType.KING)); // e5
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.PAWN)); // d4

        assertThat(board.isInCheck(Color.BLACK)).isTrue();
    }

    @Test
    void kingNotInCheckWhenAttackerIsBlockedByInterveningPiece() {
        Board board = Board.empty();
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.KING)); // d4
        board.placePiece(Square.of(0, 3), new Piece(Color.BLACK, PieceType.ROOK)); // a4, misma fila
        board.placePiece(Square.of(2, 3), new Piece(Color.WHITE, PieceType.PAWN)); // c4, bloquea el rayo

        assertThat(board.isInCheck(Color.WHITE)).isFalse();
    }

    @Test
    void kingNotInCheckFromOwnPieceAlignedLikeAnAttacker() {
        Board board = Board.empty();
        board.placePiece(Square.of(3, 3), new Piece(Color.WHITE, PieceType.KING)); // d4
        board.placePiece(Square.of(0, 3), new Piece(Color.WHITE, PieceType.ROOK)); // a4, misma fila, pero propia

        assertThat(board.isInCheck(Color.WHITE)).isFalse();
    }

    @Test
    void isInCheckThrowsWhenKingIsMissingFromTheBoard() {
        Board board = Board.empty(); // sin ningún rey colocado

        assertThatThrownBy(() -> board.isInCheck(Color.WHITE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void copyIsIndependentFromTheOriginalBoard() {
        Board original = Board.empty();
        original.placePiece(Square.of(4, 4), new Piece(Color.WHITE, PieceType.KING)); // e5

        Board copy = original.copy();
        copy.placePiece(Square.of(0, 0), new Piece(Color.BLACK, PieceType.ROOK)); // a1, solo en la copia

        assertThat(original.pieceAt(Square.of(0, 0))).isNull();
        assertThat(copy.pieceAt(Square.of(0, 0))).isNotNull();
    }

    @Test
    void applyMoveMovesThePieceAndClearsTheOriginSquare() {
        Board board = Board.empty();
        Square from = Square.of(4, 1); // e2
        Square to = Square.of(4, 3); // e4
        board.placePiece(from, new Piece(Color.WHITE, PieceType.PAWN));

        board.applyMove(new Move(from, to));

        assertThat(board.pieceAt(from)).isNull();
        assertThat(board.pieceAt(to)).isEqualTo(new Piece(Color.WHITE, PieceType.PAWN));
    }

    @Test
    void applyMoveRemovesTheCapturedPiece() {
        Board board = Board.empty();
        Square from = Square.of(3, 3); // d4
        Square to = Square.of(4, 4); // e5
        board.placePiece(from, new Piece(Color.WHITE, PieceType.BISHOP));
        board.placePiece(to, new Piece(Color.BLACK, PieceType.KNIGHT));

        board.applyMove(new Move(from, to));

        assertThat(board.pieceAt(to)).isEqualTo(new Piece(Color.WHITE, PieceType.BISHOP));
    }

    @Test
    void applyMoveHandlesEnPassantCaptureRemovingThePassedPawn() {
        Board board = Board.empty();
        Square from = Square.of(4, 4); // e5
        Square to = Square.of(3, 5); // d6, casilla vacía
        Square capturedPawnSquare = Square.of(3, 4); // d5, donde de verdad está el peón negro
        board.placePiece(from, new Piece(Color.WHITE, PieceType.PAWN));
        board.placePiece(capturedPawnSquare, new Piece(Color.BLACK, PieceType.PAWN));
        board.setEnPassantTarget(to);

        board.applyMove(new Move(from, to));

        assertThat(board.pieceAt(to)).isEqualTo(new Piece(Color.WHITE, PieceType.PAWN));
        assertThat(board.pieceAt(capturedPawnSquare)).isNull(); // el peón capturado desaparece
    }

    @Test
    void applyMovePromotesPawnToRequestedType() {
        Board board = Board.empty();
        Square from = Square.of(4, 6); // e7
        Square to = Square.of(4, 7); // e8
        board.placePiece(from, new Piece(Color.WHITE, PieceType.PAWN));

        board.applyMove(new Move(from, to, PieceType.QUEEN));

        assertThat(board.pieceAt(to)).isEqualTo(new Piece(Color.WHITE, PieceType.QUEEN));
    }

    @Test
    void applyMoveSetsEnPassantTargetOnlyAfterADoublePush() {
        Board board = Board.empty();
        Square from = Square.of(4, 1); // e2
        Square to = Square.of(4, 3); // e4, doble paso
        board.placePiece(from, new Piece(Color.WHITE, PieceType.PAWN));

        board.applyMove(new Move(from, to));

        assertThat(board.enPassantTarget()).isEqualTo(Square.of(4, 2)); // e3
    }

    @Test
    void applyMoveClearsEnPassantTargetAfterANonDoublePushMove() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 1), new Piece(Color.WHITE, PieceType.PAWN)); // e2
        board.applyMove(new Move(Square.of(4, 1), Square.of(4, 3))); // e2-e4, fija d.. e3 target
        assertThat(board.enPassantTarget()).isEqualTo(Square.of(4, 2)); // e3

        // Cualquier otra jugada (que no sea otro doble paso) debe limpiarlo.
        board.placePiece(Square.of(0, 6), new Piece(Color.BLACK, PieceType.PAWN)); // a7
        board.applyMove(new Move(Square.of(0, 6), Square.of(0, 5))); // a7-a6, empuje simple

        assertThat(board.enPassantTarget()).isNull();
    }

    @Test
    void applyMoveResetsHalfmoveClockOnPawnMoveOrCapture() {
        Board board = Board.empty();
        board.placePiece(Square.of(1, 3), new Piece(Color.WHITE, PieceType.KNIGHT)); // b4
        board.placePiece(Square.of(2, 5), new Piece(Color.BLACK, PieceType.PAWN)); // c6

        // Jugada de una pieza que no es peón ni captura: el contador sube.
        board.applyMove(new Move(Square.of(1, 3), Square.of(3, 4))); // Nb4-d5
        assertThat(board.halfmoveClock()).isEqualTo(1);

        // Captura: el contador se reinicia a 0.
        board.applyMove(new Move(Square.of(3, 4), Square.of(2, 5))); // Nd5xc6
        assertThat(board.halfmoveClock()).isZero();
    }

    @Test
    void applyMoveIncrementsFullmoveNumberOnlyAfterBlacksMove() {
        Board board = Board.initial();
        assertThat(board.fullmoveNumber()).isEqualTo(1);

        board.applyMove(new Move(Square.of(4, 1), Square.of(4, 3))); // e2-e4 (blancas)
        assertThat(board.fullmoveNumber()).isEqualTo(1); // todavía no sube

        board.applyMove(new Move(Square.of(4, 6), Square.of(4, 4))); // e7-e5 (negras)
        assertThat(board.fullmoveNumber()).isEqualTo(2); // ahora sí
    }

    @Test
    void applyMoveInvalidatesCastlingRightsWhenKingMoves() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 0), new Piece(Color.WHITE, PieceType.KING)); // e1
        assertThat(board.canCastleKingside(Color.WHITE)).isTrue();
        assertThat(board.canCastleQueenside(Color.WHITE)).isTrue();

        board.applyMove(new Move(Square.of(4, 0), Square.of(4, 1))); // Ke1-e2

        assertThat(board.canCastleKingside(Color.WHITE)).isFalse();
        assertThat(board.canCastleQueenside(Color.WHITE)).isFalse();
    }

    @Test
    void applyMoveInvalidatesCastlingRightWhenRookMovesFromItsOriginalSquare() {
        Board board = Board.empty();
        board.placePiece(Square.of(0, 0), new Piece(Color.WHITE, PieceType.ROOK)); // a1
        assertThat(board.canCastleQueenside(Color.WHITE)).isTrue();

        board.applyMove(new Move(Square.of(0, 0), Square.of(0, 4))); // Ta1-a5

        assertThat(board.canCastleQueenside(Color.WHITE)).isFalse();
        assertThat(board.canCastleKingside(Color.WHITE)).isTrue(); // el otro lado no se ve afectado
    }

    @Test
    void applyMoveInvalidatesCastlingRightWhenAnUnmovedRookIsCaptured() {
        Board board = Board.empty();
        board.placePiece(Square.of(0, 7), new Piece(Color.BLACK, PieceType.ROOK)); // a8, sin mover
        board.placePiece(Square.of(1, 6), new Piece(Color.WHITE, PieceType.BISHOP)); // b7
        assertThat(board.canCastleQueenside(Color.BLACK)).isTrue();

        board.applyMove(new Move(Square.of(1, 6), Square.of(0, 7))); // Bb7xa8

        assertThat(board.canCastleQueenside(Color.BLACK)).isFalse();
    }

    @Test
    void pinnedRookCanOnlyMoveAlongThePinLine() {
        // Torre blanca clavada por una torre negra en la misma columna que el rey: puede
        // moverse a lo largo de esa columna (incluida la captura de la pieza que clava),
        // pero no puede salirse de ella sin dejar al rey en jaque.
        Board board = Board.empty();
        Square kingSquare = Square.of(4, 0); // e1
        Square rookSquare = Square.of(4, 3); // e4
        board.placePiece(kingSquare, new Piece(Color.WHITE, PieceType.KING));
        board.placePiece(rookSquare, new Piece(Color.WHITE, PieceType.ROOK));
        board.placePiece(Square.of(4, 7), new Piece(Color.BLACK, PieceType.ROOK)); // e8

        List<Move> rookMoves = board.legalMoves().stream()
                .filter(m -> m.from().equals(rookSquare))
                .toList();

        assertThat(rookMoves).hasSize(6); // e2, e3, e5, e6, e7, e8 (captura)
        assertThat(rookMoves).extracting(Move::to).allMatch(square -> square.file() == 4);
    }

    @Test
    void kingCannotMoveToASquareStillAttackedAlongTheSameRank() {
        Board board = Board.empty();
        Square kingSquare = Square.of(4, 0); // e1, en jaque desde a1
        board.placePiece(kingSquare, new Piece(Color.WHITE, PieceType.KING));
        board.placePiece(Square.of(0, 0), new Piece(Color.BLACK, PieceType.ROOK)); // a1

        List<Move> kingMoves = board.legalMoves();

        assertThat(kingMoves).hasSize(3); // d2, e2, f2 — d1 y f1 siguen en la fila atacada
        assertThat(kingMoves).extracting(Move::to).doesNotContain(Square.of(3, 0), Square.of(5, 0));
    }

    @Test
    void initialBoardHasExactlyTwentyLegalMovesForWhite() {
        // Comprobación clásica: la posición inicial tiene exactamente 20 jugadas posibles
        // (16 de peones + 4 de caballos) — si esto falla, algo se rompió en la tubería
        // completa de generación + filtrado, no en una pieza aislada.
        Board board = Board.initial();

        assertThat(board.legalMoves()).hasSize(20);
        assertThat(board.isCheckmate()).isFalse();
        assertThat(board.isStalemate()).isFalse();
    }

    @Test
    void classicBackRankCheckmate() {
        Board board = Board.empty();
        board.placePiece(Square.of(7, 0), new Piece(Color.WHITE, PieceType.KING)); // h1
        board.placePiece(Square.of(6, 1), new Piece(Color.WHITE, PieceType.PAWN)); // g2
        board.placePiece(Square.of(7, 1), new Piece(Color.WHITE, PieceType.PAWN)); // h2
        board.placePiece(Square.of(0, 0), new Piece(Color.BLACK, PieceType.ROOK)); // a1

        assertThat(board.isInCheck(Color.WHITE)).isTrue();
        assertThat(board.legalMoves()).isEmpty();
        assertThat(board.isCheckmate()).isTrue();
        assertThat(board.isStalemate()).isFalse();
    }

    @Test
    void classicStalemateWithLoneKingAndOpposingQueen() {
        Board board = Board.empty();
        board.placePiece(Square.of(0, 0), new Piece(Color.WHITE, PieceType.KING)); // a1
        board.placePiece(Square.of(2, 1), new Piece(Color.BLACK, PieceType.QUEEN)); // c2

        assertThat(board.isInCheck(Color.WHITE)).isFalse(); // el rey en sí no está atacado
        assertThat(board.legalMoves()).isEmpty(); // pero las 3 casillas de huida sí lo están
        assertThat(board.isStalemate()).isTrue();
        assertThat(board.isCheckmate()).isFalse();
    }

    @Test
    void whiteCanCastleBothSidesWhenPathIsClearAndNotAttacked() {
        Board board = Board.empty();
        Square kingSquare = Square.of(4, 0); // e1
        board.placePiece(kingSquare, new Piece(Color.WHITE, PieceType.KING));
        board.placePiece(Square.of(0, 0), new Piece(Color.WHITE, PieceType.ROOK)); // a1
        board.placePiece(Square.of(7, 0), new Piece(Color.WHITE, PieceType.ROOK)); // h1

        List<Move> kingMoves = board.legalMoves().stream()
                .filter(m -> m.from().equals(kingSquare))
                .toList();

        assertThat(kingMoves).hasSize(7); // 5 normales + enroque corto y largo
        assertThat(kingMoves).extracting(Move::to)
                .contains(Square.of(6, 0), Square.of(2, 0)); // g1, c1
    }

    @Test
    void whiteCannotCastleKingsideWhenPathIsBlocked() {
        Board board = Board.empty();
        Square kingSquare = Square.of(4, 0); // e1
        board.placePiece(kingSquare, new Piece(Color.WHITE, PieceType.KING));
        board.placePiece(Square.of(7, 0), new Piece(Color.WHITE, PieceType.ROOK)); // h1
        board.placePiece(Square.of(5, 0), new Piece(Color.WHITE, PieceType.KNIGHT)); // f1, bloquea el paso

        // Filtramos por el rey: la propia torre en h1 tiene un movimiento normal a g1
        // (deslizar por la fila), que contaminaría el "doesNotContain" si no aislamos.
        List<Move> kingMoves = board.legalMoves().stream()
                .filter(m -> m.from().equals(kingSquare))
                .toList();

        // g1 solo es alcanzable enrocando (está a 2 casillas) — su ausencia aquí confirma
        // que el enroque específicamente se bloqueó, no que f1 esté ocupado sin más.
        assertThat(kingMoves).extracting(Move::to).doesNotContain(Square.of(6, 0));
    }

    @Test
    void whiteCannotCastleWhenKingIsCurrentlyInCheck() {
        Board board = Board.empty();
        Square kingSquare = Square.of(4, 0); // e1
        board.placePiece(kingSquare, new Piece(Color.WHITE, PieceType.KING));
        board.placePiece(Square.of(0, 0), new Piece(Color.WHITE, PieceType.ROOK)); // a1
        board.placePiece(Square.of(7, 0), new Piece(Color.WHITE, PieceType.ROOK)); // h1
        board.placePiece(Square.of(4, 7), new Piece(Color.BLACK, PieceType.ROOK)); // e8, jaque por columna

        List<Move> kingMoves = board.legalMoves();

        assertThat(kingMoves).extracting(Move::to)
                .doesNotContain(Square.of(6, 0), Square.of(2, 0)); // ni g1 ni c1
    }

    @Test
    void whiteCannotCastleKingsideWhenTransitSquareIsAttacked() {
        Board board = Board.empty();
        Square kingSquare = Square.of(4, 0); // e1
        board.placePiece(kingSquare, new Piece(Color.WHITE, PieceType.KING));
        board.placePiece(Square.of(0, 0), new Piece(Color.WHITE, PieceType.ROOK)); // a1
        board.placePiece(Square.of(7, 0), new Piece(Color.WHITE, PieceType.ROOK)); // h1
        board.placePiece(Square.of(5, 7), new Piece(Color.BLACK, PieceType.ROOK)); // f8, ataca f1 (de paso)

        // Filtramos por el rey: la torre en h1 también podría deslizar hasta g1 como
        // movimiento normal, contaminando el "doesNotContain" si no aislamos.
        List<Move> kingMoves = board.legalMoves().stream()
                .filter(m -> m.from().equals(kingSquare))
                .toList();

        assertThat(kingMoves).extracting(Move::to).doesNotContain(Square.of(6, 0)); // g1 bloqueado
        assertThat(kingMoves).extracting(Move::to).contains(Square.of(2, 0)); // c1 no se ve afectado
    }

    @Test
    void whiteCannotCastleKingsideWhenDestinationSquareIsAttacked() {
        Board board = Board.empty();
        Square kingSquare = Square.of(4, 0); // e1
        board.placePiece(kingSquare, new Piece(Color.WHITE, PieceType.KING));
        board.placePiece(Square.of(0, 0), new Piece(Color.WHITE, PieceType.ROOK)); // a1
        board.placePiece(Square.of(7, 0), new Piece(Color.WHITE, PieceType.ROOK)); // h1
        board.placePiece(Square.of(6, 7), new Piece(Color.BLACK, PieceType.ROOK)); // g8, ataca g1 directamente

        // Mismo motivo: aislamos los movimientos del rey frente a los de la torre en h1.
        List<Move> kingMoves = board.legalMoves().stream()
                .filter(m -> m.from().equals(kingSquare))
                .toList();

        assertThat(kingMoves).extracting(Move::to).doesNotContain(Square.of(6, 0)); // g1 bloqueado
        assertThat(kingMoves).extracting(Move::to).contains(Square.of(2, 0)); // c1 no se ve afectado
    }

    @Test
    void whiteCannotCastleQueensideAfterRookHasMovedAndReturned() {
        Board board = Board.empty();
        Square kingSquare = Square.of(4, 0); // e1
        board.placePiece(kingSquare, new Piece(Color.WHITE, PieceType.KING));
        board.placePiece(Square.of(0, 0), new Piece(Color.WHITE, PieceType.ROOK)); // a1
        assertThat(board.canCastleQueenside(Color.WHITE)).isTrue();

        // Ta1-a2 y de vuelta Ta2-a1: los dos applyMove() alternan turno automáticamente
        // (blanco->negro->blanco), así que no hace falta setTurn() entre medias.
        board.applyMove(new Move(Square.of(0, 0), Square.of(0, 1)));
        board.applyMove(new Move(Square.of(0, 1), Square.of(0, 0)));

        assertThat(board.canCastleQueenside(Color.WHITE)).isFalse(); // el derecho no vuelve

        List<Move> kingMoves = board.legalMoves().stream()
                .filter(m -> m.from().equals(kingSquare))
                .toList();
        assertThat(kingMoves).extracting(Move::to).doesNotContain(Square.of(2, 0)); // c1
    }

    @Test
    void whiteCannotCastleWhenNoRookIsPhysicallyPresentAtTheCorner() {
        // Los flags de enroque parten en true incluso en un tablero vacío — esto
        // comprueba la verificación defensiva de que de verdad hay una torre en la
        // esquina, no solo confiar en el flag.
        Board board = Board.empty();
        Square kingSquare = Square.of(4, 0); // e1, sin ninguna torre en el tablero
        board.placePiece(kingSquare, new Piece(Color.WHITE, PieceType.KING));

        List<Move> kingMoves = board.legalMoves();

        assertThat(kingMoves).extracting(Move::to).doesNotContain(Square.of(6, 0), Square.of(2, 0));
    }

    @Test
    void blackCanCastleKingsideSymmetricToWhite() {
        Board board = Board.empty();
        board.setTurn(Color.BLACK);
        Square kingSquare = Square.of(4, 7); // e8
        board.placePiece(kingSquare, new Piece(Color.BLACK, PieceType.KING));
        board.placePiece(Square.of(7, 7), new Piece(Color.BLACK, PieceType.ROOK)); // h8

        List<Move> kingMoves = board.legalMoves().stream()
                .filter(m -> m.from().equals(kingSquare))
                .toList();

        assertThat(kingMoves).extracting(Move::to).contains(Square.of(6, 7)); // g8
    }

    @Test
    void applyMoveExecutesKingsideCastlingByAlsoMovingTheRook() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 0), new Piece(Color.WHITE, PieceType.KING)); // e1
        board.placePiece(Square.of(7, 0), new Piece(Color.WHITE, PieceType.ROOK)); // h1

        board.applyMove(new Move(Square.of(4, 0), Square.of(6, 0))); // e1-g1

        assertThat(board.pieceAt(Square.of(6, 0))).isEqualTo(new Piece(Color.WHITE, PieceType.KING)); // g1
        assertThat(board.pieceAt(Square.of(5, 0))).isEqualTo(new Piece(Color.WHITE, PieceType.ROOK)); // f1
        assertThat(board.pieceAt(Square.of(4, 0))).isNull(); // e1
        assertThat(board.pieceAt(Square.of(7, 0))).isNull(); // h1
    }

    @Test
    void applyMoveExecutesQueensideCastlingByAlsoMovingTheRook() {
        Board board = Board.empty();
        board.placePiece(Square.of(4, 0), new Piece(Color.WHITE, PieceType.KING)); // e1
        board.placePiece(Square.of(0, 0), new Piece(Color.WHITE, PieceType.ROOK)); // a1

        board.applyMove(new Move(Square.of(4, 0), Square.of(2, 0))); // e1-c1

        assertThat(board.pieceAt(Square.of(2, 0))).isEqualTo(new Piece(Color.WHITE, PieceType.KING)); // c1
        assertThat(board.pieceAt(Square.of(3, 0))).isEqualTo(new Piece(Color.WHITE, PieceType.ROOK)); // d1
        assertThat(board.pieceAt(Square.of(4, 0))).isNull(); // e1
        assertThat(board.pieceAt(Square.of(0, 0))).isNull(); // a1
    }

    @Test
    void toFenMatchesStandardStartingPosition() {
        Board board = Board.initial();

        assertThat(board.toFen())
                .isEqualTo("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    @Test
    void toFenReflectsAMoveTurnChangeAndEnPassantTarget() {
        Board board = Board.initial();
        board.applyMove(new Move(Square.of(4, 1), Square.of(4, 3))); // 1. e4

        // FEN estándar y muy citado tras 1.e4: turno negro, e3 como objetivo de captura
        // al paso, contador de 50 movimientos a 0 (jugada de peón), enroque intacto.
        assertThat(board.toFen())
                .isEqualTo("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
    }

    @Test
    void fromAlgebraicParsesCorrectSquares() {
        assertThat(Square.fromAlgebraic("a1")).isEqualTo(Square.of(0, 0));
        assertThat(Square.fromAlgebraic("h8")).isEqualTo(Square.of(7, 7));
        assertThat(Square.fromAlgebraic("e4")).isEqualTo(Square.of(4, 3));
    }

    @Test
    void fromAlgebraicRejectsInvalidInput() {
        assertThatThrownBy(() -> Square.fromAlgebraic("e")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Square.fromAlgebraic("z9")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moveToUciFormatsPlainMoveCorrectly() {
        Move move = new Move(Square.of(4, 1), Square.of(4, 3)); // e2-e4

        assertThat(move.toUci()).isEqualTo("e2e4");
    }

    @Test
    void moveToUciIncludesPromotionLetter() {
        Move move = new Move(Square.of(4, 6), Square.of(4, 7), PieceType.QUEEN); // e7-e8=Q

        assertThat(move.toUci()).isEqualTo("e7e8q");
    }

    @Test
    void moveFromUciParsesPlainMove() {
        assertThat(Move.fromUci("e2e4")).isEqualTo(new Move(Square.of(4, 1), Square.of(4, 3)));
    }

    @Test
    void moveFromUciParsesPromotion() {
        assertThat(Move.fromUci("e7e8q"))
                .isEqualTo(new Move(Square.of(4, 6), Square.of(4, 7), PieceType.QUEEN));
    }

    @Test
    void moveHistoryRecordsMovesInOrderAsTheyAreApplied() {
        Board board = Board.initial();

        board.applyMove(new Move(Square.of(4, 1), Square.of(4, 3))); // e2-e4
        board.applyMove(new Move(Square.of(4, 6), Square.of(4, 4))); // e7-e5

        assertThat(board.moveHistory()).containsExactly(
                new Move(Square.of(4, 1), Square.of(4, 3)),
                new Move(Square.of(4, 6), Square.of(4, 4))
        );
    }

    @Test
    void copyIncludesTheMoveHistorySoFar() {
        Board board = Board.initial();
        board.applyMove(new Move(Square.of(4, 1), Square.of(4, 3))); // e2-e4

        Board copy = board.copy();

        assertThat(copy.moveHistory()).containsExactly(new Move(Square.of(4, 1), Square.of(4, 3)));
    }
}
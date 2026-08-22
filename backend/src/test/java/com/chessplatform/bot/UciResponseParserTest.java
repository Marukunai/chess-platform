package com.chessplatform.bot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UciResponseParserTest {

    @Test
    void extractBestMoveReadsTheMoveFromASimpleLine() {
        assertThat(UciResponseParser.extractBestMove("bestmove e2e4")).isEqualTo("e2e4");
    }

    @Test
    void extractBestMoveIgnoresThePonderPart() {
        assertThat(UciResponseParser.extractBestMove("bestmove e2e4 ponder e7e5")).isEqualTo("e2e4");
    }

    @Test
    void extractBestMoveReadsAPromotionMoveCorrectly() {
        assertThat(UciResponseParser.extractBestMove("bestmove a7a8q")).isEqualTo("a7a8q");
    }

    @Test
    void extractBestMoveReturnsNullWhenThereIsNoLegalMove() {
        // Lo que manda Stockfish de verdad cuando la posición no tiene ninguna jugada
        // legal — no debería llegar a pasar en la práctica (BotMoveService solo pide un
        // movimiento cuando la partida sigue en curso), pero el analizador lo maneja
        // igualmente sin lanzar nada raro.
        assertThat(UciResponseParser.extractBestMove("bestmove (none)")).isNull();
    }

    @Test
    void extractBestMoveReturnsNullForALineThatIsNotABestMoveLine() {
        assertThat(UciResponseParser.extractBestMove("info depth 10 score cp 25")).isNull();
    }

    @Test
    void extractBestMoveReturnsNullForNull() {
        assertThat(UciResponseParser.extractBestMove(null)).isNull();
    }

    @Test
    void isUciOkMatchesExactlyTheUciOkLine() {
        assertThat(UciResponseParser.IS_UCI_OK.test("uciok")).isTrue();
        assertThat(UciResponseParser.IS_UCI_OK.test("id name Stockfish 16")).isFalse();
    }

    @Test
    void isUciOkIsTolerantOfSurroundingWhitespace() {
        assertThat(UciResponseParser.IS_UCI_OK.test("  uciok  ")).isTrue();
    }

    @Test
    void isReadyOkMatchesExactlyTheReadyOkLine() {
        assertThat(UciResponseParser.IS_READY_OK.test("readyok")).isTrue();
        assertThat(UciResponseParser.IS_READY_OK.test("uciok")).isFalse();
    }

    @Test
    void isBestMoveLineMatchesAnyLineStartingWithBestmove() {
        assertThat(UciResponseParser.IS_BEST_MOVE_LINE.test("bestmove e2e4")).isTrue();
        assertThat(UciResponseParser.IS_BEST_MOVE_LINE.test("info depth 10")).isFalse();
    }

    @Test
    void isBestMoveLineIsFalseForNull() {
        assertThat(UciResponseParser.IS_BEST_MOVE_LINE.test(null)).isFalse();
    }
}
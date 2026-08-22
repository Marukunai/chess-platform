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

    @Test
    void parseScoreReadsCentipawnsFromARealisticInfoLine() {
        UciResponseParser.ScoreInfo score = UciResponseParser.parseScore(
                "info depth 15 seldepth 20 multipv 1 score cp 120 nodes 123456 nps 500000 time 250 pv e2e4 e7e5");

        assertThat(score.centipawns()).isEqualTo(120);
        assertThat(score.mateIn()).isNull();
    }

    @Test
    void parseScoreReadsANegativeCentipawnScore() {
        // Negativo == mala para quien mueve en esta posición, no un error de análisis.
        UciResponseParser.ScoreInfo score = UciResponseParser.parseScore(
                "info depth 12 score cp -350 nodes 98765");

        assertThat(score.centipawns()).isEqualTo(-350);
    }

    @Test
    void parseScoreReadsAMateScore() {
        UciResponseParser.ScoreInfo score = UciResponseParser.parseScore(
                "info depth 10 seldepth 12 multipv 1 score mate 3 nodes 5432");

        assertThat(score.mateIn()).isEqualTo(3);
        assertThat(score.centipawns()).isNull();
    }

    @Test
    void parseScoreReadsANegativeMateScore() {
        // Mate en contra de quien mueve — el rival tiene mate forzado, no al revés.
        UciResponseParser.ScoreInfo score = UciResponseParser.parseScore(
                "info depth 8 score mate -2 nodes 1234");

        assertThat(score.mateIn()).isEqualTo(-2);
    }

    @Test
    void parseScoreReturnsNullForAnInfoLineWithoutScoreYet() {
        // Las primeras líneas "info" de una búsqueda a veces no llevan puntuación
        // todavía (solo profundidad/nodos) — no es un error, simplemente no hay nada
        // que extraer de esta línea en concreto.
        assertThat(UciResponseParser.parseScore("info depth 1 currmove e2e4 currmovenumber 1")).isNull();
    }

    @Test
    void parseScoreReturnsNullForABestMoveLine() {
        assertThat(UciResponseParser.parseScore("bestmove e2e4 ponder e7e5")).isNull();
    }

    @Test
    void parseScoreReturnsNullForNull() {
        assertThat(UciResponseParser.parseScore(null)).isNull();
    }
}
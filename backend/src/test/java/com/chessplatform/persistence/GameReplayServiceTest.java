package com.chessplatform.persistence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameReplayServiceTest {

    private final GameReplayService replayService = new GameReplayService();

    @Test
    void reconstructFenPositionsReturnsOnlyTheInitialPositionWhenMoveListIsEmpty() {
        assertThat(replayService.reconstructFenPositions(null)).hasSize(1);
        assertThat(replayService.reconstructFenPositions("")).hasSize(1);
        assertThat(replayService.reconstructFenPositions("  ")).hasSize(1);
    }

    @Test
    void reconstructFenPositionsStartsWithTheStandardStartingPosition() {
        var positions = replayService.reconstructFenPositions(null);

        assertThat(positions.get(0)).isEqualTo("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    @Test
    void reconstructFenPositionsHasOneMorePositionThanMoves() {
        var positions = replayService.reconstructFenPositions("e2e4 e7e5 g1f3");

        assertThat(positions).hasSize(4); // posición inicial + 3 jugadas
    }

    @Test
    void reconstructFenPositionsMatchesTheKnownFenAfterOnePawnMove() {
        var positions = replayService.reconstructFenPositions("e2e4");

        // Mismo FEN estándar y muy citado ya usado en BoardTest tras 1.e4.
        assertThat(positions.get(1))
                .isEqualTo("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
    }
}
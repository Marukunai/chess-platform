package com.chessplatform.persistence;

import com.chessplatform.persistence.GameReplayService.ReplayResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameReplayServiceTest {

    private final GameReplayService replayService = new GameReplayService();

    @Test
    void reconstructReplayReturnsOnlyTheInitialPositionWhenMoveListIsEmpty() {
        assertThat(replayService.reconstructReplay(null).fenPositions()).hasSize(1);
        assertThat(replayService.reconstructReplay("").fenPositions()).hasSize(1);
        assertThat(replayService.reconstructReplay("  ").fenPositions()).hasSize(1);
    }

    @Test
    void reconstructReplayStartsWithTheStandardStartingPosition() {
        ReplayResult replay = replayService.reconstructReplay(null);

        assertThat(replay.fenPositions().getFirst()).isEqualTo("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    @Test
    void reconstructReplayHasOneMorePositionThanMoves() {
        ReplayResult replay = replayService.reconstructReplay("e2e4 e7e5 g1f3");

        assertThat(replay.fenPositions()).hasSize(4); // posición inicial + 3 jugadas
        assertThat(replay.notation()).hasSize(3);
    }

    @Test
    void reconstructReplayMatchesTheKnownFenAfterOnePawnMove() {
        ReplayResult replay = replayService.reconstructReplay("e2e4");

        // Mismo FEN estándar y muy citado ya usado en BoardTest tras 1.e4.
        assertThat(replay.fenPositions().get(1))
                .isEqualTo("rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1");
    }

    @Test
    void reconstructReplayProducesReadableNotationNotRawUci() {
        // Mate del necio: 1. f3 e5 2. g4 Qh4# — la última jugada es la dama capturando
        // el peón de g4 sin necesitar comerse nada por el camino, así que solo Qh4 sin x.
        ReplayResult replay = replayService.reconstructReplay("f2f3 e7e5 g2g4 d8h4");

        assertThat(replay.notation()).containsExactly("f3", "e5", "g4", "Qh4");
    }

    @Test
    void reconstructReplayMarksCapturesWithX() {
        // 1. e4 d5 2. exd5 — el peón blanco captura en d5.
        ReplayResult replay = replayService.reconstructReplay("e2e4 d7d5 e4d5");

        assertThat(replay.notation()).containsExactly("e4", "d5", "exd5");
    }
}
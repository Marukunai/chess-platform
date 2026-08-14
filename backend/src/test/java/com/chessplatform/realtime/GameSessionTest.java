package com.chessplatform.realtime;

import com.chessplatform.engine.Color;
import com.chessplatform.engine.Move;
import com.chessplatform.engine.Piece;
import com.chessplatform.engine.PieceType;
import com.chessplatform.engine.Square;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GameSessionTest {

    /**
     * Clock de test controlable manualmente con advance(), en vez de depender de
     * Thread.sleep() real — más rápido y determinista para probar lógica de reloj.
     */
    private static class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant start) {
            this.instant = start;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static GameSession newSession(Duration initialTime, Duration increment, MutableClock clock) {
        // Constructor con Clock es package-private a propósito (ver GameSession) — este
        // test vive en el mismo paquete, así que puede usarlo directamente.
        return new GameSession("white-player", "black-player", initialTime, increment, clock);
    }

    @Test
    void newSessionStartsWithFullTimeForBothPlayers() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = newSession(Duration.ofMinutes(10), Duration.ZERO, clock);

        assertThat(session.timeRemaining(Color.WHITE)).isEqualTo(Duration.ofMinutes(10));
        assertThat(session.timeRemaining(Color.BLACK)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void applyMoveConsumesTimeFromTheMoverOnly() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = newSession(Duration.ofMinutes(10), Duration.ZERO, clock);

        clock.advance(Duration.ofSeconds(5));
        session.applyMove(new Move(Square.of(4, 1), Square.of(4, 3))); // e2-e4

        assertThat(session.board().turn()).isEqualTo(Color.BLACK);
        assertThat(session.timeRemaining(Color.WHITE)).isEqualTo(Duration.ofMinutes(10).minusSeconds(5));
        // El reloj de negras no corría durante la jugada de blancas — sigue intacto.
        assertThat(session.timeRemaining(Color.BLACK)).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void applyMoveAddsIncrementAfterConsumingTime() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = newSession(Duration.ofMinutes(10), Duration.ofSeconds(3), clock);

        clock.advance(Duration.ofSeconds(5));
        session.applyMove(new Move(Square.of(4, 1), Square.of(4, 3))); // e2-e4

        // Consumió 5s, ganó 3s de incremento: 10min - 5s + 3s = 10min - 2s.
        assertThat(session.timeRemaining(Color.WHITE)).isEqualTo(Duration.ofMinutes(10).minusSeconds(2));
    }

    @Test
    void timeRemainingClampsToZeroInsteadOfGoingNegative() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = newSession(Duration.ofSeconds(20), Duration.ZERO, clock);

        clock.advance(Duration.ofSeconds(50)); // muy por encima del tiempo disponible

        assertThat(session.timeRemaining(Color.WHITE)).isEqualTo(Duration.ZERO);
        assertThat(session.isTimeout(Color.WHITE)).isTrue();
    }

    @Test
    void applyMoveDelegatesToBoardAndActuallyMovesThePiece() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = newSession(Duration.ofMinutes(10), Duration.ZERO, clock);

        session.applyMove(new Move(Square.of(4, 1), Square.of(4, 3))); // e2-e4

        assertThat(session.board().pieceAt(Square.of(4, 3)))
                .isEqualTo(new Piece(Color.WHITE, PieceType.PAWN));
        assertThat(session.board().pieceAt(Square.of(4, 1))).isNull();
    }

    @Test
    void colorOfReturnsTheCorrectColorForEachPlayer() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = newSession(Duration.ofMinutes(10), Duration.ZERO, clock);

        assertThat(session.colorOf("white-player")).isEqualTo(Color.WHITE);
        assertThat(session.colorOf("black-player")).isEqualTo(Color.BLACK);
    }

    @Test
    void colorOfThrowsForAPlayerNotInTheGame() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = newSession(Duration.ofMinutes(10), Duration.ZERO, clock);

        assertThatThrownBy(() -> session.colorOf("un-espectador-cualquiera"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasExceededDisconnectGracePeriodIsFalseWhenNeverDisconnected() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = newSession(Duration.ofMinutes(10), Duration.ZERO, clock);

        assertThat(session.hasExceededDisconnectGracePeriod(Color.WHITE, Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void hasExceededDisconnectGracePeriodBecomesTrueOnceTheWindowElapses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = newSession(Duration.ofMinutes(10), Duration.ZERO, clock);

        session.markDisconnected(Color.WHITE);
        assertThat(session.hasExceededDisconnectGracePeriod(Color.WHITE, Duration.ofSeconds(30))).isFalse();

        clock.advance(Duration.ofSeconds(31));
        assertThat(session.hasExceededDisconnectGracePeriod(Color.WHITE, Duration.ofSeconds(30))).isTrue();

        // El otro jugador ni se ha desconectado — no debería verse afectado.
        assertThat(session.hasExceededDisconnectGracePeriod(Color.BLACK, Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void markConnectedClearsTheDisconnectState() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = newSession(Duration.ofMinutes(10), Duration.ZERO, clock);

        session.markDisconnected(Color.WHITE);
        clock.advance(Duration.ofSeconds(31));
        session.markConnected(Color.WHITE); // vuelve a tiempo

        assertThat(session.hasExceededDisconnectGracePeriod(Color.WHITE, Duration.ofSeconds(30))).isFalse();
    }
}
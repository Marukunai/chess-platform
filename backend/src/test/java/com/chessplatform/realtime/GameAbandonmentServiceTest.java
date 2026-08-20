package com.chessplatform.realtime;

import com.chessplatform.engine.Color;
import com.chessplatform.realtime.dto.GameOverMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameAbandonmentServiceTest {

    /** Mismo patrón que GameTimeoutServiceTest — controla el tiempo sin Thread.sleep(). */
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

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private com.chessplatform.rating.GameResultRecorder gameResultRecorder;

    @Mock
    private com.chessplatform.presence.PresenceService presenceService;

    private GameSessionRegistry sessionRegistry;
    private GameAbandonmentService abandonmentService;

    @BeforeEach
    void setUp() {
        sessionRegistry = new GameSessionRegistry();
        GameEndNotifier gameEndNotifier = new GameEndNotifier(sessionRegistry, messagingTemplate, gameResultRecorder, presenceService);
        abandonmentService = new GameAbandonmentService(sessionRegistry, gameEndNotifier);
    }

    private static GameSession newSession(MutableClock clock) {
        // Constructor con Clock es package-private — este test vive en el mismo paquete.
        return new GameSession("white-player", "black-player", Duration.ofMinutes(10), Duration.ZERO, clock);
    }

    @Test
    void tickDoesNothingWhenNoOneHasDisconnected() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = newSession(clock);
        sessionRegistry.create(session);

        abandonmentService.tick();

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        assertThat(sessionRegistry.find(session.gameId())).isPresent();
    }

    @Test
    void tickDoesNothingWhileWithinTheGracePeriod() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = newSession(clock);
        sessionRegistry.create(session);

        session.markDisconnected(Color.WHITE);
        clock.advance(Duration.ofSeconds(10)); // menos que los 30s de ventana

        abandonmentService.tick();

        assertThat(sessionRegistry.find(session.gameId())).isPresent();
    }

    @Test
    void tickDeclaresBlackTheWinnerWhenWhiteExceedsTheGracePeriod() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = newSession(clock);
        sessionRegistry.create(session);

        session.markDisconnected(Color.WHITE);
        clock.advance(Duration.ofSeconds(31));

        abandonmentService.tick();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), payload.capture());
        GameOverMessage gameOver = (GameOverMessage) payload.getValue();
        assertThat(gameOver.result()).isEqualTo("0-1"); // ganan negras
        assertThat(gameOver.reason()).isEqualTo("abandonment");
        assertThat(sessionRegistry.find(session.gameId())).isEmpty();
    }

    @Test
    void reconnectingBeforeTheGracePeriodExpiresPreventsAbandonment() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = newSession(clock);
        sessionRegistry.create(session);

        session.markDisconnected(Color.WHITE);
        clock.advance(Duration.ofSeconds(20));
        session.markConnected(Color.WHITE); // vuelve a tiempo
        clock.advance(Duration.ofSeconds(20)); // más de 30s desde la desconexión original, pero ya reconectó

        abandonmentService.tick();

        assertThat(sessionRegistry.find(session.gameId())).isPresent();
    }

    @Test
    void tickDeclaresADrawWhenBothPlayersExceedTheGracePeriod() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = newSession(clock);
        sessionRegistry.create(session);

        session.markDisconnected(Color.WHITE);
        session.markDisconnected(Color.BLACK);
        clock.advance(Duration.ofSeconds(31));

        abandonmentService.tick();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), payload.capture());
        GameOverMessage gameOver = (GameOverMessage) payload.getValue();
        assertThat(gameOver.result()).isEqualTo("1/2-1/2");
        assertThat(gameOver.reason()).isEqualTo("abandonment");
    }
}
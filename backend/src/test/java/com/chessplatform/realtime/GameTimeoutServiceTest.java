package com.chessplatform.realtime;

import com.chessplatform.achievement.AchievementUnlockService;
import com.chessplatform.presence.PresenceService;
import com.chessplatform.rating.GameResultRecorder;
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
class GameTimeoutServiceTest {

    /** Mismo patrón que GameSessionTest — controla el paso del tiempo sin Thread.sleep(). */
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
    private GameResultRecorder gameResultRecorder;

    @Mock
    private PresenceService presenceService;

    @Mock
    private AchievementUnlockService achievementUnlockService;

    private GameSessionRegistry sessionRegistry;
    private GameTimeoutService timeoutService;

    @BeforeEach
    void setUp() {
        sessionRegistry = new GameSessionRegistry();
        GameEndNotifier gameEndNotifier = new GameEndNotifier(sessionRegistry, messagingTemplate, gameResultRecorder,
                presenceService, achievementUnlockService);
        timeoutService = new GameTimeoutService(sessionRegistry, gameEndNotifier);
    }

    @Test
    void tickEndsGameWhenThePlayerToMoveRunsOutOfTime() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = new GameSession(
                "white-player", "black-player", Duration.ofSeconds(10), Duration.ZERO, clock);
        sessionRegistry.create(session);

        clock.advance(Duration.ofSeconds(15)); // blancas se han quedado sin tiempo

        timeoutService.tick();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(anyString(), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(GameOverMessage.class);

        GameOverMessage gameOver = (GameOverMessage) payload.getValue();
        assertThat(gameOver.result()).isEqualTo("0-1"); // ganan negras, blancas agotaron el tiempo
        assertThat(gameOver.reason()).isEqualTo("timeout");

        assertThat(sessionRegistry.find(session.gameId())).isEmpty();
    }

    @Test
    void tickDoesNothingWhenNoOneHasTimedOut() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = new GameSession(
                "white-player", "black-player", Duration.ofMinutes(10), Duration.ZERO, clock);
        sessionRegistry.create(session);

        clock.advance(Duration.ofSeconds(5)); // muy lejos de agotar los 10 minutos

        timeoutService.tick();

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
        assertThat(sessionRegistry.find(session.gameId())).isPresent();
    }

    @Test
    void tickIgnoresTheOpponentsStoredTimeSinceTheirClockIsNotRunning() {
        // Es el turno de blancas; que negras tuvieran poco tiempo no importaría — su
        // reloj no corre mientras no es su turno. Aquí ambos tienen tiempo de sobra,
        // así que esto confirma que el barrido no termina la partida sin motivo.
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        GameSession session = new GameSession(
                "white-player", "black-player", Duration.ofMinutes(10), Duration.ZERO, clock);
        sessionRegistry.create(session);

        timeoutService.tick();

        assertThat(sessionRegistry.find(session.gameId())).isPresent();
    }
}
package com.chessplatform.matchmaking;

import com.chessplatform.matchmaking.dto.MatchFoundMessage;
import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchmakingServiceTest {

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

    private MutableClock clock;
    private MatchmakingQueue queue;
    private GameSessionRegistry sessionRegistry;
    private MatchmakingService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        queue = new MatchmakingQueue();
        queue.setClock(clock);
        sessionRegistry = new GameSessionRegistry();
        service = new MatchmakingService(queue, sessionRegistry, messagingTemplate);
    }

    @Test
    void tickDoesNothingWithFewerThanTwoPlayers() {
        queue.enqueue("solo-player", "solo-player", null, 1500, TimeControl.BLITZ);

        service.tick();

        assertThat(queue.snapshot()).hasSize(1);
        assertThat(sessionRegistry.activeCount()).isZero();
    }

    @Test
    void tickMatchesTwoPlayersWithCloseRatingsAndSameTimeControl() {
        queue.enqueue("alice", "alice", null, 1500, TimeControl.BLITZ);
        queue.enqueue("bob", "bob", null, 1520, TimeControl.BLITZ); // dentro de la ventana inicial (100)

        service.tick();

        assertThat(queue.snapshot()).isEmpty(); // ambos ya emparejados, fuera de la cola
        assertThat(sessionRegistry.activeCount()).isEqualTo(1);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(2)).convertAndSend(anyString(), payload.capture());
        assertThat(payload.getAllValues()).allMatch(p -> p instanceof MatchFoundMessage);
    }

    @Test
    void tickSetsBothUsernamesOnTheCreatedSession() {
        queue.enqueue("alice-id", "alice", null, 1500, TimeControl.BLITZ);
        queue.enqueue("bob-id", "bob", null, 1520, TimeControl.BLITZ);

        service.tick();

        GameSession session = sessionRegistry.allSessions().iterator().next();
        // El color se sortea al azar (ver pairUp), así que comprobamos el PAR de
        // nombres sin asumir quién quedó blancas — lo importante es que cada nombre
        // viaje pegado al id de jugador correcto, no a cuál le tocó cada color.
        assertThat(Set.of(session.whiteUsername(), session.blackUsername()))
                .isEqualTo(Set.of("alice", "bob"));
    }

    @Test
    void tickSetsBothAvatarsOnTheCreatedSession() {
        queue.enqueue("alice-id", "alice", "https://ejemplo.com/alice.png", 1500, TimeControl.BLITZ);
        queue.enqueue("bob-id", "bob", null, 1520, TimeControl.BLITZ); // bob sin avatar fijado

        service.tick();

        GameSession session = sessionRegistry.allSessions().iterator().next();
        Set<String> avatars = new java.util.HashSet<>();
        avatars.add(session.whiteAvatarUrl());
        avatars.add(session.blackAvatarUrl());
        assertThat(avatars).contains("https://ejemplo.com/alice.png", (String) null);
    }

    @Test
    void tickDoesNotMatchPlayersWithDifferentTimeControls() {
        queue.enqueue("alice", "alice", null, 1500, TimeControl.BLITZ);
        queue.enqueue("bob", "bob", null, 1500, TimeControl.RAPID);

        service.tick();

        assertThat(queue.snapshot()).hasSize(2);
        assertThat(sessionRegistry.activeCount()).isZero();
    }

    @Test
    void tickDoesNotMatchRatingsFarApartInitially() {
        queue.enqueue("alice", "alice", null, 1000, TimeControl.BLITZ);
        queue.enqueue("bob", "bob", null, 2000, TimeControl.BLITZ); // 1000 puntos de diferencia

        service.tick();

        assertThat(queue.snapshot()).hasSize(2);
        assertThat(sessionRegistry.activeCount()).isZero();
    }

    @Test
    void tickEventuallyMatchesFarApartRatingsAsTheWaitGrows() {
        queue.enqueue("alice", "alice", null, 1000, TimeControl.BLITZ);
        queue.enqueue("bob", "bob", null, 2000, TimeControl.BLITZ);

        clock.advance(Duration.ofSeconds(60)); // la ventana ya creció mucho para ambos
        service.tick();

        assertThat(sessionRegistry.activeCount()).isEqualTo(1);
    }

    @Test
    void matchedPlayersGetAssignedToOppositeColorsInTheSameGame() {
        queue.enqueue("alice", "alice", null, 1500, TimeControl.BLITZ);
        queue.enqueue("bob", "bob", null, 1500, TimeControl.BLITZ);

        service.tick();

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, times(2)).convertAndSend(anyString(), payload.capture());

        List<MatchFoundMessage> messages = payload.getAllValues().stream()
                .map(m -> (MatchFoundMessage) m)
                .toList();

        assertThat(messages.get(0).gameId()).isEqualTo(messages.get(1).gameId());
        assertThat(Set.of(messages.get(0).color(), messages.get(1).color())).isEqualTo(Set.of("white", "black"));

        GameSession session = sessionRegistry.find(messages.get(0).gameId()).orElseThrow();
        assertThat(Set.of(session.whitePlayerId(), session.blackPlayerId())).isEqualTo(Set.of("alice", "bob"));
    }
}
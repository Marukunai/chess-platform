package com.chessplatform.realtime;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GameSessionRegistryTest {

    private static GameSession newSession(String whitePlayerId, String blackPlayerId) {
        return new GameSession(whitePlayerId, blackPlayerId, Duration.ofMinutes(10), Duration.ZERO);
    }

    @Test
    void findByPlayerIdFindsTheGameWhereThePlayerIsWhite() {
        GameSessionRegistry registry = new GameSessionRegistry();
        GameSession session = newSession("alice", "bob");
        registry.create(session);

        assertThat(registry.findByPlayerId("alice")).contains(session);
    }

    @Test
    void findByPlayerIdFindsTheGameWhereThePlayerIsBlack() {
        GameSessionRegistry registry = new GameSessionRegistry();
        GameSession session = newSession("alice", "bob");
        registry.create(session);

        assertThat(registry.findByPlayerId("bob")).contains(session);
    }

    @Test
    void findByPlayerIdReturnsEmptyWhenThePlayerHasNoActiveGame() {
        GameSessionRegistry registry = new GameSessionRegistry();
        registry.create(newSession("alice", "bob"));

        assertThat(registry.findByPlayerId("carol")).isEmpty();
    }
}
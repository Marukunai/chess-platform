package com.chessplatform.matchmaking;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchmakingQueueTest {

    @Test
    void enqueueAddsPlayerToSnapshot() {
        MatchmakingQueue queue = new MatchmakingQueue();

        queue.enqueue("alice", 1500, TimeControl.BLITZ);

        assertThat(queue.snapshot()).hasSize(1);
        assertThat(queue.snapshot().get(0).playerId()).isEqualTo("alice");
    }

    @Test
    void enqueueReplacesAnyExistingEntryForTheSamePlayer() {
        MatchmakingQueue queue = new MatchmakingQueue();

        queue.enqueue("alice", 1500, TimeControl.BLITZ);
        queue.enqueue("alice", 1600, TimeControl.RAPID); // se vuelve a apuntar con otros datos

        assertThat(queue.snapshot()).hasSize(1);
        assertThat(queue.snapshot().get(0).rating()).isEqualTo(1600);
        assertThat(queue.snapshot().get(0).timeControl()).isEqualTo(TimeControl.RAPID);
    }

    @Test
    void removeTakesThePlayerOutOfTheQueue() {
        MatchmakingQueue queue = new MatchmakingQueue();
        queue.enqueue("alice", 1500, TimeControl.BLITZ);

        queue.remove("alice");

        assertThat(queue.snapshot()).isEmpty();
    }

    @Test
    void removeAllRemovesExactlyTheGivenPlayersAndKeepsTheRest() {
        MatchmakingQueue queue = new MatchmakingQueue();
        queue.enqueue("alice", 1500, TimeControl.BLITZ);
        queue.enqueue("bob", 1500, TimeControl.BLITZ);
        queue.enqueue("carol", 1500, TimeControl.BLITZ);

        var toRemove = queue.snapshot().stream()
                .filter(p -> !p.playerId().equals("carol"))
                .toList();
        queue.removeAll(toRemove);

        assertThat(queue.snapshot()).hasSize(1);
        assertThat(queue.snapshot().get(0).playerId()).isEqualTo("carol");
    }
}
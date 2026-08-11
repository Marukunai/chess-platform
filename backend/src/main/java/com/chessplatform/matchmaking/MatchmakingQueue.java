package com.chessplatform.matchmaking;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class MatchmakingQueue {

    public record WaitingPlayer(String playerId, int rating, Instant queuedAt) {
    }

    private final ConcurrentLinkedQueue<WaitingPlayer> queue = new ConcurrentLinkedQueue<>();

    public void enqueue(String playerId, int rating) {
        queue.add(new WaitingPlayer(playerId, rating, Instant.now()));
    }

    public void remove(String playerId) {
        queue.removeIf(p -> p.playerId().equals(playerId));
    }

    public ConcurrentLinkedQueue<WaitingPlayer> snapshot() {
        return queue;
    }
}

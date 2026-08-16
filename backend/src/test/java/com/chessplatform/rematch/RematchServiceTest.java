package com.chessplatform.rematch;

import com.chessplatform.engine.Color;
import com.chessplatform.matchmaking.TimeControl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RematchServiceTest {

    private final RematchService service = new RematchService();

    private static RematchService.PendingRematch rematchFrom(String fromUserId, String toUserId) {
        return new RematchService.PendingRematch(
                fromUserId, "nombre-de-" + fromUserId, toUserId,
                Color.WHITE, Color.BLACK, TimeControl.BLITZ, "BLITZ");
    }

    @Test
    void findIsEmptyWhenNobodyHasProposedAnything() {
        assertThat(service.find("bob-id")).isEmpty();
    }

    @Test
    void proposeMakesItFindableByTheTarget() {
        service.propose(rematchFrom("alice-id", "bob-id"));

        assertThat(service.find("bob-id")).contains(rematchFrom("alice-id", "bob-id"));
    }

    @Test
    void proposingASecondTimeReplacesTheFirstOffer() {
        service.propose(rematchFrom("alice-id", "bob-id"));
        service.propose(rematchFrom("carol-id", "bob-id"));

        assertThat(service.find("bob-id")).map(RematchService.PendingRematch::fromUserId).contains("carol-id");
    }

    @Test
    void clearRemovesThePendingOffer() {
        service.propose(rematchFrom("alice-id", "bob-id"));

        service.clear("bob-id");

        assertThat(service.find("bob-id")).isEmpty();
    }
}
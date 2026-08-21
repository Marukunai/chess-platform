package com.chessplatform.achievement;

import com.chessplatform.achievement.dto.AchievementLeaderboardEntryResponse;
import com.chessplatform.achievement.dto.AchievementProgressResponse;
import com.chessplatform.persistence.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AchievementControllerTest {

    @Mock
    private AchievementService achievementService;

    private AchievementController controller;

    @BeforeEach
    void setUp() {
        controller = new AchievementController(achievementService);
    }

    private static void setId(User user, String id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void forUserMapsEachDetailedAchievementToItsResponseShape() {
        UserStatsSnapshot snapshot = new UserStatsSnapshot(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1500, Set.of(), false, false, 0);
        AchievementDefinition primeraPartida = AchievementCatalog.ALL.stream()
                .filter(def -> def.id().equals("primera-partida"))
                .findFirst().orElseThrow();
        java.time.Instant unlockedAt = java.time.Instant.parse("2026-01-15T00:00:00Z");
        when(achievementService.detailedProgressFor("alice-id")).thenReturn(List.of(
                new AchievementService.DetailedAchievementProgress(primeraPartida, snapshot, unlockedAt, 12.5, "bob")));

        List<AchievementProgressResponse> response = controller.forUser("alice-id");

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo("primera-partida");
        assertThat(response.getFirst().currentProgress()).isEqualTo(1);
        assertThat(response.getFirst().target()).isEqualTo(1);
        assertThat(response.getFirst().unlocked()).isTrue();
        assertThat(response.getFirst().unlockedAt()).isEqualTo(unlockedAt.toString());
        assertThat(response.getFirst().rarityPercent()).isEqualTo(12.5);
        assertThat(response.getFirst().firstUnlockedByUsername()).isEqualTo("bob");
    }

    @Test
    void forUserLeavesUnlockedAtAndFirstUnlockedByNullWhenNobodyHasItYet() {
        UserStatsSnapshot snapshot = new UserStatsSnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1500, Set.of(), false, false, 0);
        AchievementDefinition primeraPartida = AchievementCatalog.ALL.stream()
                .filter(def -> def.id().equals("primera-partida"))
                .findFirst().orElseThrow();
        when(achievementService.detailedProgressFor("alice-id")).thenReturn(List.of(
                new AchievementService.DetailedAchievementProgress(primeraPartida, snapshot, null, 0.0, null)));

        List<AchievementProgressResponse> response = controller.forUser("alice-id");

        assertThat(response.getFirst().unlockedAt()).isNull();
        assertThat(response.getFirst().firstUnlockedByUsername()).isNull();
    }

    @Test
    void leaderboardRanksStartingAtOneAndIncludesTheTotalCatalogSize() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        when(achievementService.leaderboard()).thenReturn(List.of(
                new AchievementService.UserAchievementCount(alice, 15),
                new AchievementService.UserAchievementCount(bob, 3)
        ));

        List<AchievementLeaderboardEntryResponse> leaderboard = controller.leaderboard();

        assertThat(leaderboard).hasSize(2);
        assertThat(leaderboard.getFirst().rank()).isEqualTo(1);
        assertThat(leaderboard.getFirst().username()).isEqualTo("alice");
        assertThat(leaderboard.getFirst().unlockedCount()).isEqualTo(15);
        assertThat(leaderboard.getFirst().totalCount()).isEqualTo(AchievementCatalog.ALL.size());
        assertThat(leaderboard.get(1).rank()).isEqualTo(2);
        assertThat(leaderboard.get(1).username()).isEqualTo("bob");
    }

    @Test
    void leaderboardReturnsAnEmptyListWhenThereAreNoUsers() {
        when(achievementService.leaderboard()).thenReturn(List.of());

        List<AchievementLeaderboardEntryResponse> leaderboard = controller.leaderboard();

        assertThat(leaderboard).isEmpty();
    }
}
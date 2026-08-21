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
    void forUserMapsEachAchievementToItsResponseShape() {
        UserStatsSnapshot snapshot = new UserStatsSnapshot(1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1500, Set.of(), false, false, 0);
        AchievementDefinition primeraPartida = AchievementCatalog.ALL.stream()
                .filter(def -> def.id().equals("primera-partida"))
                .findFirst().orElseThrow();
        when(achievementService.progressFor("alice-id")).thenReturn(
                List.of(new AchievementService.AchievementProgress(primeraPartida, snapshot)));

        List<AchievementProgressResponse> response = controller.forUser("alice-id");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).id()).isEqualTo("primera-partida");
        assertThat(response.get(0).currentProgress()).isEqualTo(1);
        assertThat(response.get(0).target()).isEqualTo(1);
        assertThat(response.get(0).unlocked()).isTrue();
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
        assertThat(leaderboard.get(0).rank()).isEqualTo(1);
        assertThat(leaderboard.get(0).username()).isEqualTo("alice");
        assertThat(leaderboard.get(0).unlockedCount()).isEqualTo(15);
        assertThat(leaderboard.get(0).totalCount()).isEqualTo(AchievementCatalog.ALL.size());
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
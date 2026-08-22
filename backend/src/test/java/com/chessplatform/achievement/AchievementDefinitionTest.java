package com.chessplatform.achievement;

import com.chessplatform.rating.GameMode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementDefinitionTest {

    private static UserStatsSnapshot snapshotWithGamesPlayed(int gamesPlayed) {
        return new UserStatsSnapshot(gamesPlayed, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1500, Set.of(), false, false, 0, 0, 0, 0, 0, 0);
    }

    @Test
    void progressForNeverExceedsTheTargetEvenWhenTheRawValueIsHigher() {
        AchievementDefinition def = new AchievementDefinition("test", "Test", "desc",
                AchievementCategory.GENERAL, 10, UserStatsSnapshot::gamesPlayed);

        assertThat(def.progressFor(snapshotWithGamesPlayed(300))).isEqualTo(10);
    }

    @Test
    void progressForReturnsTheRawValueWhenBelowTarget() {
        AchievementDefinition def = new AchievementDefinition("test", "Test", "desc",
                AchievementCategory.GENERAL, 10, UserStatsSnapshot::gamesPlayed);

        assertThat(def.progressFor(snapshotWithGamesPlayed(4))).isEqualTo(4);
    }

    @Test
    void isUnlockedForIsFalseBelowTheTarget() {
        AchievementDefinition def = new AchievementDefinition("test", "Test", "desc",
                AchievementCategory.GENERAL, 10, UserStatsSnapshot::gamesPlayed);

        assertThat(def.isUnlockedFor(snapshotWithGamesPlayed(9))).isFalse();
    }

    @Test
    void isUnlockedForIsTrueExactlyAtTheTarget() {
        AchievementDefinition def = new AchievementDefinition("test", "Test", "desc",
                AchievementCategory.GENERAL, 10, UserStatsSnapshot::gamesPlayed);

        assertThat(def.isUnlockedFor(snapshotWithGamesPlayed(10))).isTrue();
    }

    @Test
    void isUnlockedForIsTrueWellAboveTheTarget() {
        AchievementDefinition def = new AchievementDefinition("test", "Test", "desc",
                AchievementCategory.GENERAL, 10, UserStatsSnapshot::gamesPlayed);

        assertThat(def.isUnlockedFor(snapshotWithGamesPlayed(500))).isTrue();
    }

    @Test
    void modeBasedAchievementsAreBinaryZeroOrOne() {
        AchievementDefinition velocista = AchievementCatalog.ALL.stream()
                .filter(def -> def.id().equals("velocista"))
                .findFirst().orElseThrow();

        UserStatsSnapshot withoutBullet = new UserStatsSnapshot(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1500, Set.of(GameMode.BLITZ), false, false, 0, 0, 0, 0, 0, 0);
        UserStatsSnapshot withBullet = new UserStatsSnapshot(1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1500, Set.of(GameMode.BULLET), false, false, 0, 0, 0, 0, 0, 0);

        assertThat(velocista.isUnlockedFor(withoutBullet)).isFalse();
        assertThat(velocista.isUnlockedFor(withBullet)).isTrue();
    }
}
package com.chessplatform.achievement;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AchievementCatalogTest {

    @Test
    void hasAtLeastFifteenAchievements() {
        // Empezamos con 20 y los ampliamos a 38 — el número exacto puede seguir
        // creciendo con el tiempo, así que esta comprobación solo fija un mínimo
        // razonable en vez de un rango cerrado que habría que tocar cada vez.
        assertThat(AchievementCatalog.ALL.size()).isGreaterThanOrEqualTo(15);
    }

    @Test
    void everyAchievementHasAUniqueId() {
        Set<String> ids = AchievementCatalog.ALL.stream()
                .map(AchievementDefinition::id)
                .collect(Collectors.toSet());

        assertThat(ids).hasSameSizeAs(AchievementCatalog.ALL);
    }

    @Test
    void everyAchievementHasANonBlankNameAndDescription() {
        assertThat(AchievementCatalog.ALL).allSatisfy(def -> {
            assertThat(def.name()).isNotBlank();
            assertThat(def.description()).isNotBlank();
        });
    }

    @Test
    void everyAchievementHasAPositiveTarget() {
        assertThat(AchievementCatalog.ALL).allMatch(def -> def.target() > 0);
    }

    @Test
    void everyCategoryIsUsedByAtLeastOneAchievement() {
        Set<AchievementCategory> categoriesUsed = AchievementCatalog.ALL.stream()
                .map(AchievementDefinition::category)
                .collect(Collectors.toSet());

        assertThat(categoriesUsed).containsExactlyInAnyOrder(AchievementCategory.values());
    }
}
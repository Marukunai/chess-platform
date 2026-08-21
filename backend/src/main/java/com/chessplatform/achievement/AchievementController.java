package com.chessplatform.achievement;

import com.chessplatform.achievement.dto.AchievementLeaderboardEntryResponse;
import com.chessplatform.achievement.dto.AchievementProgressResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Todo de lectura pública a propósito, igual que el perfil y el ranking de rating — ver
 * los progresos de logros de cualquiera (no solo los tuyos) es parte del punto de tener
 * un ranking global de logros.
 */
@RestController
@RequestMapping("/api/achievements")
public class AchievementController {

    private final AchievementService achievementService;

    public AchievementController(AchievementService achievementService) {
        this.achievementService = achievementService;
    }

    @GetMapping("/{userId}")
    public List<AchievementProgressResponse> forUser(@PathVariable String userId) {
        return achievementService.progressFor(userId).stream()
                .map(p -> new AchievementProgressResponse(
                        p.definition().id(), p.definition().name(), p.definition().description(),
                        p.definition().category().name(), p.currentProgress(), p.definition().target(), p.unlocked()))
                .toList();
    }

    @GetMapping("/leaderboard")
    public List<AchievementLeaderboardEntryResponse> leaderboard() {
        List<AchievementService.UserAchievementCount> ranked = achievementService.leaderboard();
        int totalCount = AchievementCatalog.ALL.size();
        return IntStream.range(0, ranked.size())
                .mapToObj(i -> {
                    AchievementService.UserAchievementCount entry = ranked.get(i);
                    return new AchievementLeaderboardEntryResponse(
                            i + 1, entry.user().getId(), entry.user().getUsername(), entry.unlockedCount(), totalCount);
                })
                .toList();
    }
}
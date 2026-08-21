package com.chessplatform.achievement;

import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.entity.UserRating;
import com.chessplatform.persistence.repository.DirectMessageRepository;
import com.chessplatform.persistence.repository.FriendshipRepository;
import com.chessplatform.persistence.repository.GameRepository;
import com.chessplatform.persistence.repository.UserRatingRepository;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.rating.GameMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private FriendshipRepository friendshipRepository;

    @Mock
    private DirectMessageRepository directMessageRepository;

    @Mock
    private UserRatingRepository userRatingRepository;

    @Mock
    private UserRepository userRepository;

    private AchievementService service;

    @BeforeEach
    void setUp() {
        service = new AchievementService(gameRepository, friendshipRepository, directMessageRepository,
                userRatingRepository, userRepository);
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

    /** Igual que setId() — sin esto, createdAt queda fijado al Instant.now() real del momento en que corre el test, lo que hace que "hace cuántos días te registraste" dependa de CUÁNDO se ejecute el test en vez de ser un número fijo y comprobable. */
    private static void setCreatedAt(User user, Instant createdAt) {
        try {
            Field field = User.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(user, createdAt);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static Game gameOf(User white, User black, String result, String reason) {
        Game game = new Game(white, black, "5+3");
        game.setResult(result);
        game.setReason(reason);
        return game;
    }

    @Test
    void computeSnapshotCountsGamesWonAndDrawnCorrectlyRegardlessOfColor() {
        User viewer = new User("alice", "hash");
        setId(viewer, "alice-id");
        User opponent = new User("bob", "hash");
        setId(opponent, "bob-id");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(
                        gameOf(viewer, opponent, "1-0", "checkmate"), // alice blancas, gana por jaque mate
                        gameOf(opponent, viewer, "1-0", "checkmate"), // alice negras, pierde
                        gameOf(viewer, opponent, "1/2-1/2", "agreement") // tablas
                ));
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of());

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.gamesPlayed()).isEqualTo(3);
        assertThat(snapshot.gamesWon()).isEqualTo(1);
        assertThat(snapshot.gamesDrawn()).isEqualTo(1);
        assertThat(snapshot.checkmateWins()).isEqualTo(1);
    }

    @Test
    void computeSnapshotOnlyCountsCheckmateWinsAmongTheVictories() {
        User viewer = new User("alice", "hash");
        setId(viewer, "alice-id");
        User opponent = new User("bob", "hash");
        setId(opponent, "bob-id");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(
                        gameOf(viewer, opponent, "1-0", "checkmate"),
                        gameOf(viewer, opponent, "1-0", "resignation") // victoria, pero no por jaque mate
                ));
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of());

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.gamesWon()).isEqualTo(2);
        assertThat(snapshot.checkmateWins()).isEqualTo(1);
    }

    @Test
    void computeSnapshotUsesTheHighestRatingAcrossAllModes() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        UserRating blitzRating = new UserRating(alice, GameMode.BLITZ);
        blitzRating.applyRatingUpdate(1650, 100, 0.06);
        UserRating bulletRating = new UserRating(alice, GameMode.BULLET);
        bulletRating.applyRatingUpdate(1420, 100, 0.06); // más bajo, no debe ganar
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of(blitzRating, bulletRating));

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.highestRating()).isEqualTo(1650);
    }

    @Test
    void computeSnapshotDefaultsHighestRatingWhenNoModeHasBeenPlayed() {
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of());

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.highestRating()).isEqualTo(1500);
        assertThat(snapshot.modesPlayed()).isEmpty();
    }

    @Test
    void computeSnapshotCollectsWhichModesHaveBeenPlayed() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("alice-id"))
                .thenReturn(List.of(new UserRating(alice, GameMode.BULLET), new UserRating(alice, GameMode.CLASSICAL)));

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.modesPlayed()).containsExactlyInAnyOrder(GameMode.BULLET, GameMode.CLASSICAL);
    }

    @Test
    void progressForEvaluatesTheWholeCatalogAgainstTheSameSnapshot() {
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of());

        List<AchievementService.AchievementProgress> progress = service.progressFor("alice-id");

        assertThat(progress).hasSize(AchievementCatalog.ALL.size());
        // Sin haber jugado nada, ninguno debería estar desbloqueado.
        assertThat(progress).noneMatch(AchievementService.AchievementProgress::unlocked);
    }

    @Test
    void unlockedCountForCountsOnlyAchievementsThatAreActuallyUnlocked() {
        User viewer = new User("alice", "hash");
        setId(viewer, "alice-id");
        User opponent = new User("bob", "hash");
        setId(opponent, "bob-id");
        // Justo lo necesario para desbloquear "primera-partida" y "primera-victoria",
        // nada más — el resto del catálogo debería seguir bloqueado.
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(gameOf(viewer, opponent, "1-0", "resignation")));
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of());

        int unlockedCount = service.unlockedCountFor("alice-id");

        assertThat(unlockedCount).isEqualTo(2); // primera-partida + primera-victoria
    }

    @Test
    void leaderboardSortsUsersByUnlockedCountDescending() {
        User alice = new User("alice", "hash"); // más logros
        setId(alice, "alice-id");
        User bob = new User("bob", "hash"); // ninguno
        setId(bob, "bob-id");
        when(userRepository.findByDeletedAtIsNull()).thenReturn(List.of(bob, alice));

        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(gameOf(alice, bob, "1-0", "resignation")));
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("bob-id", "bob-id"))
                .thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendships(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id(org.mockito.ArgumentMatchers.anyString())).thenReturn(0L);
        when(userRatingRepository.findByUser_Id(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());

        List<AchievementService.UserAchievementCount> ranked = service.leaderboard();

        assertThat(ranked.get(0).user().getUsername()).isEqualTo("alice");
        assertThat(ranked.get(0).unlockedCount()).isGreaterThan(ranked.get(1).unlockedCount());
    }

    @Test
    void computeSnapshotCountsGamesLostSeparatelyFromDrawsAndWins() {
        User viewer = new User("alice", "hash");
        setId(viewer, "alice-id");
        User opponent = new User("bob", "hash");
        setId(opponent, "bob-id");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(
                        gameOf(opponent, viewer, "1-0", "checkmate"), // alice negras, pierde por jaque mate
                        gameOf(viewer, opponent, "0-1", "resignation") // alice blancas, se rinde ella misma — vuelve a perder
                ));
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countByRecipient_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countDistinctConversationPartners("alice-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of());
        when(userRepository.findById("alice-id")).thenReturn(java.util.Optional.of(viewer));

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.gamesLost()).isEqualTo(2);
        assertThat(snapshot.gamesWon()).isZero();
    }

    @Test
    void computeSnapshotCountsStalemateDrawsAsASubsetOfAllDraws() {
        User viewer = new User("alice", "hash");
        setId(viewer, "alice-id");
        User opponent = new User("bob", "hash");
        setId(opponent, "bob-id");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(
                        gameOf(viewer, opponent, "1/2-1/2", "stalemate"),
                        gameOf(viewer, opponent, "1/2-1/2", "agreement") // tablas, pero no por ahogado
                ));
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countByRecipient_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countDistinctConversationPartners("alice-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of());
        when(userRepository.findById("alice-id")).thenReturn(java.util.Optional.of(viewer));

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.gamesDrawn()).isEqualTo(2);
        assertThat(snapshot.stalemateDraws()).isEqualTo(1);
    }

    @Test
    void computeSnapshotReadsAvatarAndCountryFromTheUserProfile() {
        User withProfile = new User("alice", "hash");
        setId(withProfile, "alice-id");
        withProfile.updateProfile("alice", "España", "https://ejemplo.com/avatar.png");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countByRecipient_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countDistinctConversationPartners("alice-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of());
        when(userRepository.findById("alice-id")).thenReturn(java.util.Optional.of(withProfile));

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.hasAvatarSet()).isTrue();
        assertThat(snapshot.hasCountrySet()).isTrue();
    }

    @Test
    void computeSnapshotHasNeitherAvatarNorCountryByDefault() {
        User bare = new User("bob", "hash");
        setId(bare, "bob-id");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("bob-id", "bob-id"))
                .thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendships("bob-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("bob-id")).thenReturn(0L);
        when(directMessageRepository.countByRecipient_Id("bob-id")).thenReturn(0L);
        when(directMessageRepository.countDistinctConversationPartners("bob-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("bob-id")).thenReturn(List.of());
        when(userRepository.findById("bob-id")).thenReturn(java.util.Optional.of(bare));

        UserStatsSnapshot snapshot = service.computeSnapshot("bob-id");

        assertThat(snapshot.hasAvatarSet()).isFalse();
        assertThat(snapshot.hasCountrySet()).isFalse();
    }

    @Test
    void computeSnapshotCalculatesAccountAgeInDaysFromCreatedAt() {
        User thirtyDaysOldAccount = new User("alice", "hash");
        setId(thirtyDaysOldAccount, "alice-id");
        Instant now = Instant.parse("2026-02-01T00:00:00Z");
        setCreatedAt(thirtyDaysOldAccount, now.minus(30, java.time.temporal.ChronoUnit.DAYS));
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countByRecipient_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countDistinctConversationPartners("alice-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of());
        when(userRepository.findById("alice-id")).thenReturn(java.util.Optional.of(thirtyDaysOldAccount));
        service.setClock(Clock.fixed(now, ZoneOffset.UTC));

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        // Con createdAt fijado por reflexión (no al Instant.now() real del momento en
        // que corre el test) y el reloj del servicio también fijado, el resultado es un
        // número exacto y comprobable, no solo "razonable" — nada de esto depende ya de
        // CUÁNDO se ejecute el test.
        assertThat(snapshot.accountAgeDays()).isEqualTo(30);
    }

    @Test
    void computeSnapshotGivesZeroAccountAgeForABrandNewAccount() {
        User freshAccount = new User("alice", "hash");
        setId(freshAccount, "alice-id");
        Instant now = Instant.parse("2026-02-01T00:00:00Z");
        setCreatedAt(freshAccount, now); // se registró justo "ahora"
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countByRecipient_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countDistinctConversationPartners("alice-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of());
        when(userRepository.findById("alice-id")).thenReturn(java.util.Optional.of(freshAccount));
        service.setClock(Clock.fixed(now, ZoneOffset.UTC));

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.accountAgeDays()).isZero();
    }

    @Test
    void computeSnapshotReadsDirectMessagesReceivedAndDistinctPartnersFromTheRepository() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countByRecipient_Id("alice-id")).thenReturn(12L);
        when(directMessageRepository.countDistinctConversationPartners("alice-id")).thenReturn(3L);
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of());
        when(userRepository.findById("alice-id")).thenReturn(java.util.Optional.of(alice));

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.directMessagesReceived()).isEqualTo(12);
        assertThat(snapshot.distinctConversationPartners()).isEqualTo(3);
    }
}
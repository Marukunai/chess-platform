package com.chessplatform.achievement;

import com.chessplatform.persistence.entity.Friendship;
import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.entity.UserRating;
import com.chessplatform.persistence.repository.DirectMessageRepository;
import com.chessplatform.persistence.repository.FriendshipRepository;
import com.chessplatform.persistence.repository.GameRepository;
import com.chessplatform.persistence.repository.UserAchievementUnlockRepository;
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
import static org.mockito.Mockito.verify;
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

    @Mock
    private UserAchievementUnlockRepository unlockRepository;

    @Mock
    private com.chessplatform.persistence.repository.UserPuzzleAttemptRepository puzzleAttemptRepository;

    @Mock
    private com.chessplatform.persistence.repository.UserPuzzleRatingRepository puzzleRatingRepository;

    private AchievementService service;

    @BeforeEach
    void setUp() {
        service = new AchievementService(gameRepository, friendshipRepository, directMessageRepository,
                userRatingRepository, userRepository, unlockRepository, puzzleAttemptRepository, puzzleRatingRepository);
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

    private static Game gameOf(User white, User black, String result, String reason, String timeControlLabel) {
        Game game = new Game(white, black, timeControlLabel);
        game.setResult(result);
        game.setReason(reason);
        return game;
    }

    private static User botAccount(com.chessplatform.bot.BotDifficulty difficulty) {
        User bot = new User(com.chessplatform.bot.BotAccountSeeder.usernameFor(difficulty), "hash");
        setId(bot, "bot-" + difficulty.name().toLowerCase() + "-id");
        bot.markAsBot();
        return bot;
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
        when(userRepository.findByDeletedAtIsNullAndBotFalse()).thenReturn(List.of(bob, alice));

        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(gameOf(alice, bob, "1-0", "resignation")));
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("bob-id", "bob-id"))
                .thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendships(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id(org.mockito.ArgumentMatchers.anyString())).thenReturn(0L);
        when(userRatingRepository.findByUser_Id(org.mockito.ArgumentMatchers.anyString())).thenReturn(List.of());

        List<AchievementService.UserAchievementCount> ranked = service.leaderboard();

        assertThat(ranked.getFirst().user().getUsername()).isEqualTo("alice");
        assertThat(ranked.getFirst().unlockedCount()).isGreaterThan(ranked.get(1).unlockedCount());
    }

    @Test
    void leaderboardNeverAsksTheRepositoryForBotAccountsInTheFirstPlace() {
        // findByDeletedAtIsNullAndBotFalse() ya excluye los bots en la propia consulta
        // — este test solo confirma que el servicio llama a ESE método y no al genérico
        // (que sí los incluiría), sin tener que montar cuentas de bot de verdad aquí.
        when(userRepository.findByDeletedAtIsNullAndBotFalse()).thenReturn(List.of());

        service.leaderboard();

        verify(userRepository).findByDeletedAtIsNullAndBotFalse();
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

    @Test
    void detailedProgressForIncludesWhenYouUnlockedAnAchievement() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        Instant unlockedInstant = Instant.parse("2026-01-15T00:00:00Z");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countByRecipient_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countDistinctConversationPartners("alice-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of());
        when(userRepository.findById("alice-id")).thenReturn(java.util.Optional.of(alice));
        when(userRepository.countByDeletedAtIsNullAndBotFalse()).thenReturn(10L);
        com.chessplatform.persistence.entity.UserAchievementUnlock unlock =
                new com.chessplatform.persistence.entity.UserAchievementUnlock(alice, "primera-partida");
        setUnlockedAt(unlock, unlockedInstant);
        when(unlockRepository.findByUser_Id("alice-id")).thenReturn(List.of(unlock));
        when(unlockRepository.countByAchievementIdAndUser_DeletedAtIsNull(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(0L);
        when(unlockRepository.findFirstByAchievementIdOrderByUnlockedAtAsc(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Optional.empty());

        List<AchievementService.DetailedAchievementProgress> detailed = service.detailedProgressFor("alice-id");

        AchievementService.DetailedAchievementProgress primeraPartida = detailed.stream()
                .filter(p -> p.definition().id().equals("primera-partida"))
                .findFirst().orElseThrow();
        assertThat(primeraPartida.unlockedAt()).isEqualTo(unlockedInstant);

        AchievementService.DetailedAchievementProgress otro = detailed.stream()
                .filter(p -> !p.definition().id().equals("primera-partida"))
                .findFirst().orElseThrow();
        assertThat(otro.unlockedAt()).isNull();
    }

    @Test
    void detailedProgressForComputesRarityAsAPercentageOfActiveUsers() {
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countByRecipient_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countDistinctConversationPartners("alice-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of());
        when(userRepository.findById("alice-id")).thenReturn(java.util.Optional.empty());
        when(userRepository.countByDeletedAtIsNullAndBotFalse()).thenReturn(4L); // 1 de 4 == 25%
        when(unlockRepository.findByUser_Id("alice-id")).thenReturn(List.of());
        when(unlockRepository.countByAchievementIdAndUser_DeletedAtIsNull("primera-partida")).thenReturn(1L);
        when(unlockRepository.countByAchievementIdAndUser_DeletedAtIsNull(org.mockito.ArgumentMatchers.argThat(
                id -> !"primera-partida".equals(id)))).thenReturn(0L);
        when(unlockRepository.findFirstByAchievementIdOrderByUnlockedAtAsc(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Optional.empty());

        List<AchievementService.DetailedAchievementProgress> detailed = service.detailedProgressFor("alice-id");

        AchievementService.DetailedAchievementProgress primeraPartida = detailed.stream()
                .filter(p -> p.definition().id().equals("primera-partida"))
                .findFirst().orElseThrow();
        assertThat(primeraPartida.rarityPercent()).isEqualTo(25.0);
    }

    @Test
    void detailedProgressForNeverAsksTheRepositoryForBotAccountsWhenComputingRarity() {
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countByRecipient_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countDistinctConversationPartners("alice-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of());
        when(userRepository.findById("alice-id")).thenReturn(java.util.Optional.empty());
        when(userRepository.countByDeletedAtIsNullAndBotFalse()).thenReturn(1L);
        when(unlockRepository.findByUser_Id("alice-id")).thenReturn(List.of());
        when(unlockRepository.countByAchievementIdAndUser_DeletedAtIsNull(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(0L);
        when(unlockRepository.findFirstByAchievementIdOrderByUnlockedAtAsc(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Optional.empty());

        service.detailedProgressFor("alice-id");

        // La consulta que excluye bots es la que se usa como denominador — no el
        // genérico countByDeletedAtIsNull() que sí los contaría.
        verify(userRepository).countByDeletedAtIsNullAndBotFalse();
    }

    @Test
    void detailedProgressForIncludesWhoUnlockedItFirstAcrossAllUsers() {
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        when(friendshipRepository.findAcceptedFriendships("alice-id")).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countByRecipient_Id("alice-id")).thenReturn(0L);
        when(directMessageRepository.countDistinctConversationPartners("alice-id")).thenReturn(0L);
        when(userRatingRepository.findByUser_Id("alice-id")).thenReturn(List.of());
        when(userRepository.findById("alice-id")).thenReturn(java.util.Optional.empty());
        when(userRepository.countByDeletedAtIsNullAndBotFalse()).thenReturn(10L);
        when(unlockRepository.findByUser_Id("alice-id")).thenReturn(List.of());
        when(unlockRepository.countByAchievementIdAndUser_DeletedAtIsNull(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(0L);
        com.chessplatform.persistence.entity.UserAchievementUnlock bobsUnlock =
                new com.chessplatform.persistence.entity.UserAchievementUnlock(bob, "primera-partida");
        when(unlockRepository.findFirstByAchievementIdOrderByUnlockedAtAsc("primera-partida"))
                .thenReturn(java.util.Optional.of(bobsUnlock));
        when(unlockRepository.findFirstByAchievementIdOrderByUnlockedAtAsc(org.mockito.ArgumentMatchers.argThat(
                id -> !"primera-partida".equals(id)))).thenReturn(java.util.Optional.empty());

        List<AchievementService.DetailedAchievementProgress> detailed = service.detailedProgressFor("alice-id");

        AchievementService.DetailedAchievementProgress primeraPartida = detailed.stream()
                .filter(p -> p.definition().id().equals("primera-partida"))
                .findFirst().orElseThrow();
        assertThat(primeraPartida.firstUnlockedByUsername()).isEqualTo("bob");
    }

    private static void setUnlockedAt(com.chessplatform.persistence.entity.UserAchievementUnlock unlock, Instant instant) {
        try {
            Field field = com.chessplatform.persistence.entity.UserAchievementUnlock.class.getDeclaredField("unlockedAt");
            field.setAccessible(true);
            field.set(unlock, instant);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private void givenNoSocialOrRatingData(String userId) {
        when(friendshipRepository.findAcceptedFriendships(userId)).thenReturn(List.of());
        when(directMessageRepository.countBySender_Id(userId)).thenReturn(0L);
        when(directMessageRepository.countByRecipient_Id(userId)).thenReturn(0L);
        when(directMessageRepository.countDistinctConversationPartners(userId)).thenReturn(0L);
        when(userRatingRepository.findByUser_Id(userId)).thenReturn(List.of());
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.empty());
    }

    @Test
    void computeSnapshotExcludesGamesAgainstABotFromTheGeneralStats() {
        User human = new User("alice", "hash");
        setId(human, "alice-id");
        User bot = botAccount(com.chessplatform.bot.BotDifficulty.EASY);
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(gameOf(human, bot, "1-0", "checkmate")));
        givenNoSocialOrRatingData("alice-id");

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        // La partida contra el bot no cuenta para NADA de lo general — son partidas de
        // práctica, aparte, ver el javadoc de UserStatsSnapshot.
        assertThat(snapshot.gamesPlayed()).isZero();
        assertThat(snapshot.gamesWon()).isZero();
        assertThat(snapshot.checkmateWins()).isZero();
    }

    @Test
    void computeSnapshotCountsAWinAgainstEachDifficultySeparately() {
        User human = new User("alice", "hash");
        setId(human, "alice-id");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(
                        gameOf(human, botAccount(com.chessplatform.bot.BotDifficulty.EASY), "1-0", "resignation"),
                        gameOf(human, botAccount(com.chessplatform.bot.BotDifficulty.MEDIUM), "1-0", "resignation"),
                        gameOf(human, botAccount(com.chessplatform.bot.BotDifficulty.HARD), "1-0", "resignation")
                ));
        givenNoSocialOrRatingData("alice-id");

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.easyBotWins()).isEqualTo(1);
        assertThat(snapshot.mediumBotWins()).isEqualTo(1);
        assertThat(snapshot.hardBotWins()).isEqualTo(1);
    }

    @Test
    void computeSnapshotDoesNotCountLossesOrDrawsAgainstABotAsWins() {
        User human = new User("alice", "hash");
        setId(human, "alice-id");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(
                        gameOf(botAccount(com.chessplatform.bot.BotDifficulty.EASY), human, "1-0", "checkmate"), // pierde alice
                        gameOf(human, botAccount(com.chessplatform.bot.BotDifficulty.EASY), "1/2-1/2", "agreement") // tablas
                ));
        givenNoSocialOrRatingData("alice-id");

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.easyBotWins()).isZero();
    }

    @Test
    void computeSnapshotCountsHardBotWinsInBlitzSeparatelyFromOtherModes() {
        User human = new User("alice", "hash");
        setId(human, "alice-id");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(
                        gameOf(human, botAccount(com.chessplatform.bot.BotDifficulty.HARD), "1-0", "resignation", "5+3"), // blitz
                        gameOf(human, botAccount(com.chessplatform.bot.BotDifficulty.HARD), "1-0", "resignation", "1+0") // bullet, no cuenta para el logro de blitz
                ));
        givenNoSocialOrRatingData("alice-id");

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.hardBotWins()).isEqualTo(2); // las dos cuentan como victoria contra difícil en general
        assertThat(snapshot.hardBotBlitzWins()).isEqualTo(1); // pero solo una fue en blitz
        assertThat(snapshot.hardBotClassicalWins()).isZero();
    }

    @Test
    void computeSnapshotCountsHardBotWinsInClassicalSeparately() {
        User human = new User("alice", "hash");
        setId(human, "alice-id");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(
                        gameOf(human, botAccount(com.chessplatform.bot.BotDifficulty.HARD), "1-0", "resignation", "30+20")
                ));
        givenNoSocialOrRatingData("alice-id");

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.hardBotClassicalWins()).isEqualTo(1);
        assertThat(snapshot.hardBotBlitzWins()).isZero();
    }

    @Test
    void computeSnapshotCountsOnlySolvedPuzzleAttempts() {
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        givenNoSocialOrRatingData("alice-id");
        when(puzzleAttemptRepository.countByUser_IdAndSolvedTrue("alice-id")).thenReturn(7L);

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.puzzlesSolved()).isEqualTo(7);
    }

    @Test
    void computeSnapshotDefaultsPuzzleRatingTo1500WhenTheUserNeverSolvedOne() {
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        givenNoSocialOrRatingData("alice-id");

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.puzzleRating()).isEqualTo(1500);
    }

    @Test
    void computeSnapshotUsesTheActualPuzzleRatingWhenItExists() {
        User user = new User("alice", "hash");
        setId(user, "alice-id");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        givenNoSocialOrRatingData("alice-id");
        com.chessplatform.persistence.entity.UserPuzzleRating puzzleRating =
                new com.chessplatform.persistence.entity.UserPuzzleRating(user);
        puzzleRating.applyRatingUpdate(1875, 120, 0.05);
        when(puzzleRatingRepository.findByUser_Id("alice-id")).thenReturn(java.util.Optional.of(puzzleRating));

        UserStatsSnapshot snapshot = service.computeSnapshot("alice-id");

        assertThat(snapshot.puzzleRating()).isEqualTo(1875);
    }
}
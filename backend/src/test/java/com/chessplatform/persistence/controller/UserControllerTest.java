package com.chessplatform.persistence.controller;

import com.chessplatform.achievement.AchievementUnlockService;
import com.chessplatform.persistence.dto.ChangePasswordRequest;
import com.chessplatform.persistence.dto.DeleteAccountRequest;
import com.chessplatform.persistence.dto.LeaderboardEntryResponse;
import com.chessplatform.persistence.dto.UpdateProfileRequest;
import com.chessplatform.persistence.dto.UserProfileResponse;
import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.entity.UserRating;
import com.chessplatform.persistence.repository.GameRepository;
import com.chessplatform.persistence.repository.UserRatingRepository;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.rating.GameMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRatingRepository userRatingRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private AchievementUnlockService achievementUnlockService;

    // Real de verdad, no mockeado — es un algoritmo puro y rápido, y así los tests de
    // contraseña comprueban el comportamiento real (que "abc" no haga match con el hash
    // de "xyz"), no una suposición de lo que haría un mock.
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(userRepository, userRatingRepository, gameRepository, passwordEncoder,
                achievementUnlockService);
        controller.setClock(fixedClock);
    }

    private static Game gameOf(User white, User black, String result) {
        Game game = new Game(white, black, "5+3");
        game.setResult(result);
        return game;
    }

    private static Game gameOf(User white, User black, String result, String reason) {
        Game game = gameOf(white, black, result);
        game.setReason(reason);
        return game;
    }

    /**
     * User.getId() no tiene setter a propósito (lo gestiona JPA al persistir de
     * verdad) — en un test unitario puro, sin base de datos, necesitamos fijarlo a mano
     * para que la comparación por id del controlador (userId.equals(player.getId()))
     * tenga algo real que comparar.
     */
    private static void setId(User user, String id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void leaderboardRanksPlayersInOrderStartingAtOne() {
        User alice = new User("alice", "hash");
        UserRating aliceRating = new UserRating(alice, GameMode.BLITZ);
        aliceRating.applyRatingUpdate(1800, 120, 0.06);
        User bob = new User("bob", "hash");
        UserRating bobRating = new UserRating(bob, GameMode.BLITZ);
        bobRating.applyRatingUpdate(1600, 120, 0.06);
        when(userRatingRepository.findTop50ByModeAndUser_DeletedAtIsNullAndUser_BotFalseOrderByRatingDesc(GameMode.BLITZ))
                .thenReturn(List.of(aliceRating, bobRating));

        List<LeaderboardEntryResponse> leaderboard = controller.leaderboard("BLITZ");

        assertThat(leaderboard).hasSize(2);
        assertThat(leaderboard.get(0).rank()).isEqualTo(1);
        assertThat(leaderboard.get(0).username()).isEqualTo("alice");
        assertThat(leaderboard.get(0).rating()).isEqualTo(1800);
        assertThat(leaderboard.get(1).rank()).isEqualTo(2);
        assertThat(leaderboard.get(1).username()).isEqualTo("bob");
    }

    @Test
    void leaderboardDefaultsToBlitzWhenNoModeIsGiven() {
        // El propio @RequestParam(defaultValue = "BLITZ") solo actúa cuando Spring MVC
        // resuelve una petición HTTP real — llamando al método Java directamente (como
        // en cualquiera de estos tests) ese valor por defecto no se aplica solo, así que
        // aquí se comprueba pasándolo explícito en vez de omitirlo.
        when(userRatingRepository.findTop50ByModeAndUser_DeletedAtIsNullAndUser_BotFalseOrderByRatingDesc(GameMode.BLITZ))
                .thenReturn(List.of());

        controller.leaderboard("BLITZ");

        verify(userRatingRepository).findTop50ByModeAndUser_DeletedAtIsNullAndUser_BotFalseOrderByRatingDesc(GameMode.BLITZ);
    }

    @Test
    void leaderboardRejectsAnUnknownMode() {
        assertThatThrownBy(() -> controller.leaderboard("ULTRA-INVENTADO"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void leaderboardIsCaseInsensitiveForTheModeParameter() {
        when(userRatingRepository.findTop50ByModeAndUser_DeletedAtIsNullAndUser_BotFalseOrderByRatingDesc(GameMode.RAPID))
                .thenReturn(List.of());

        controller.leaderboard("rapid");

        verify(userRatingRepository).findTop50ByModeAndUser_DeletedAtIsNullAndUser_BotFalseOrderByRatingDesc(GameMode.RAPID);
    }

    @Test
    void profileThrowsNotFoundWhenTheUserDoesNotExist() {
        when(userRepository.findById("missing-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.profile("missing-user"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void profileComputesRecordFromSavedGamesRegardlessOfColorPlayed() {
        User viewer = new User("alice", "hash");
        setId(viewer, "alice-id");
        User opponent = new User("bob", "hash");
        setId(opponent, "bob-id");

        when(userRepository.findById("alice-id")).thenReturn(Optional.of(viewer));
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(
                        gameOf(viewer, opponent, "1-0"), // alice blancas, gana
                        gameOf(opponent, viewer, "1-0"), // alice negras, pierde (ganan blancas=opponent)
                        gameOf(viewer, opponent, "1/2-1/2") // tablas
                ));

        UserProfileResponse profile = controller.profile("alice-id");

        assertThat(profile.gamesPlayed()).isEqualTo(3);
        assertThat(profile.wins()).isEqualTo(1);
        assertThat(profile.losses()).isEqualTo(1);
        assertThat(profile.draws()).isEqualTo(1);
        assertThat(profile.username()).isEqualTo("alice");
    }

    @Test
    void profileShowsAllFourModesWithDefaultRatingWhenNoneHaveBeenPlayed() {
        User freshUser = new User("carol", "hash");
        setId(freshUser, "carol-id");
        when(userRepository.findById("carol-id")).thenReturn(Optional.of(freshUser));
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("carol-id", "carol-id"))
                .thenReturn(List.of());

        UserProfileResponse profile = controller.profile("carol-id");

        assertThat(profile.ratings()).hasSize(4);
        assertThat(profile.ratings()).extracting(UserProfileResponse.ModeRatingResponse::mode)
                .containsExactlyInAnyOrder("BULLET", "BLITZ", "RAPID", "CLASSICAL");
        assertThat(profile.ratings()).allSatisfy(r -> {
            assertThat(r.rating()).isEqualTo(1500);
            assertThat(r.ratingDeviation()).isEqualTo(350);
        });
    }

    @Test
    void profileShowsTheActualRatingForAModeThatHasBeenPlayed() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());
        // Sin esto, Mockito en modo estricto se queja: hay un stub para BLITZ pero
        // ratingsFor() consulta las CUATRO modalidades, y las otras tres (sin stub
        // propio) no coinciden con ninguno de los ya registrados para este método.
        when(userRatingRepository.findByUser_IdAndMode(eq("alice-id"), any(GameMode.class))).thenReturn(Optional.empty());
        UserRating blitzRating = new UserRating(alice, GameMode.BLITZ);
        blitzRating.applyRatingUpdate(1700, 90, 0.055);
        when(userRatingRepository.findByUser_IdAndMode("alice-id", GameMode.BLITZ)).thenReturn(Optional.of(blitzRating));

        UserProfileResponse profile = controller.profile("alice-id");

        UserProfileResponse.ModeRatingResponse blitz = profile.ratings().stream()
                .filter(r -> r.mode().equals("BLITZ"))
                .findFirst().orElseThrow();
        assertThat(blitz.rating()).isEqualTo(1700);
        assertThat(blitz.ratingDeviation()).isEqualTo(90);
        // Las otras tres modalidades, sin fila propia todavía, siguen en el valor por defecto.
        assertThat(profile.ratings()).filteredOn(r -> !r.mode().equals("BLITZ"))
                .allMatch(r -> r.rating() == 1500);
    }

    @Test
    void profileDoesNotCreateAnyRatingRowJustByBeingViewed() {
        User carol = new User("carol", "hash");
        setId(carol, "carol-id");
        when(userRepository.findById("carol-id")).thenReturn(Optional.of(carol));
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("carol-id", "carol-id"))
                .thenReturn(List.of());

        controller.profile("carol-id");

        // Consultar un perfil es de lectura pública y no debería tener efectos
        // secundarios en base de datos — ver el javadoc de UserController.ratingsFor().
        verify(userRatingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void profileReturnsZeroedStatsWhenTheUserHasNeverPlayed() {
        User freshUser = new User("carol", "hash");
        setId(freshUser, "carol-id");
        when(userRepository.findById("carol-id")).thenReturn(Optional.of(freshUser));
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("carol-id", "carol-id"))
                .thenReturn(List.of());

        UserProfileResponse profile = controller.profile("carol-id");

        assertThat(profile.gamesPlayed()).isZero();
        assertThat(profile.wins()).isZero();
        assertThat(profile.losses()).isZero();
        assertThat(profile.draws()).isZero();
    }

    @Test
    void profileCountsOnlyCheckmateWinsWithinWinsByCheckmate() {
        User viewer = new User("alice", "hash");
        setId(viewer, "alice-id");
        User opponent = new User("bob", "hash");
        setId(opponent, "bob-id");

        when(userRepository.findById("alice-id")).thenReturn(Optional.of(viewer));
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(
                        gameOf(viewer, opponent, "1-0", "checkmate"), // alice gana por jaque mate
                        gameOf(viewer, opponent, "1-0", "resignation"), // alice gana, pero por rendición
                        gameOf(opponent, viewer, "1-0", "checkmate") // alice pierde por jaque mate — no cuenta
                ));

        UserProfileResponse profile = controller.profile("alice-id");

        assertThat(profile.wins()).isEqualTo(2);
        assertThat(profile.winsByCheckmate()).isEqualTo(1);
    }

    @Test
    void profileListsDistinctRecentOpponentsMostRecentFirst() {
        User viewer = new User("alice", "hash");
        setId(viewer, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        User carol = new User("carol", "hash");
        setId(carol, "carol-id");

        when(userRepository.findById("alice-id")).thenReturn(Optional.of(viewer));
        // Ya vienen ordenadas por fecha descendente (así lo hace la consulta real) — la
        // más reciente contra bob es la primera de la lista, aunque haya jugado contra
        // él dos veces.
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(
                        gameOf(viewer, carol, "1-0"), // partida más reciente: contra carol
                        gameOf(viewer, bob, "1-0"), // segunda más reciente: contra bob
                        gameOf(bob, viewer, "0-1") // repetido contra bob, no debe duplicarse
                ));

        UserProfileResponse profile = controller.profile("alice-id");

        assertThat(profile.recentOpponents()).extracting(UserProfileResponse.RecentOpponent::username)
                .containsExactly("carol", "bob");
    }

    @Test
    void profileCapsRecentOpponentsAtFive() {
        User viewer = new User("alice", "hash");
        setId(viewer, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(viewer));

        List<Game> sixDistinctOpponents = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> {
                    User opponent = new User("rival" + i, "hash");
                    setId(opponent, "rival-" + i + "-id");
                    return gameOf(viewer, opponent, "1-0");
                })
                .toList();
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(sixDistinctOpponents);

        UserProfileResponse profile = controller.profile("alice-id");

        assertThat(profile.recentOpponents()).hasSize(5);
    }

    @Test
    void profileComputesWinRateAsPercentageOfGamesWon() {
        User viewer = new User("alice", "hash");
        setId(viewer, "alice-id");
        User opponent = new User("bob", "hash");
        setId(opponent, "bob-id");

        when(userRepository.findById("alice-id")).thenReturn(Optional.of(viewer));
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of(
                        gameOf(viewer, opponent, "1-0"), // gana
                        gameOf(viewer, opponent, "1-0"), // gana
                        gameOf(opponent, viewer, "1-0"), // pierde
                        gameOf(viewer, opponent, "1/2-1/2") // tablas — cuenta como jugada, no como victoria
                ));

        UserProfileResponse profile = controller.profile("alice-id");

        // 2 victorias de 4 partidas = 50%
        assertThat(profile.winRatePercent()).isEqualTo(50);
    }

    @Test
    void profileWinRateIsZeroWhenTheUserHasNeverPlayed() {
        User freshUser = new User("carol", "hash");
        setId(freshUser, "carol-id");
        when(userRepository.findById("carol-id")).thenReturn(Optional.of(freshUser));
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("carol-id", "carol-id"))
                .thenReturn(List.of());

        UserProfileResponse profile = controller.profile("carol-id");

        assertThat(profile.winRatePercent()).isZero();
    }

    private static Authentication authenticationFor(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void updateProfileRejectsWhenThereIsNoAuthentication() {
        assertThatThrownBy(() ->
                controller.updateProfile("alice-id", new UpdateProfileRequest("alice2", null, null), null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateProfileRejectsEditingSomeoneElsesProfile() {
        assertThatThrownBy(() ->
                controller.updateProfile("alice-id", new UpdateProfileRequest("alice2", null, null),
                        authenticationFor("bob-id")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void updateProfileRejectsABlankUsername() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() ->
                controller.updateProfile("alice-id", new UpdateProfileRequest("   ", null, null),
                        authenticationFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void updateProfileRejectsAUsernameAlreadyTakenBySomeoneElse() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(new User("bob", "hash")));

        assertThatThrownBy(() ->
                controller.updateProfile("alice-id", new UpdateProfileRequest("bob", null, null),
                        authenticationFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void updateProfileAllowsKeepingYourOwnCurrentUsername() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());

        // No debe consultar findByUsername("alice") ni tratarlo como duplicado consigo misma.
        UserProfileResponse profile = controller.updateProfile(
                "alice-id", new UpdateProfileRequest("alice", "España", null), authenticationFor("alice-id"));

        assertThat(profile.username()).isEqualTo("alice");
        assertThat(profile.country()).isEqualTo("España");
    }

    @Test
    void updateProfileSavesTheNewFieldsAndReturnsThem() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("alice_nueva")).thenReturn(Optional.empty());
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());

        UserProfileResponse profile = controller.updateProfile(
                "alice-id",
                new UpdateProfileRequest("alice_nueva", "España", "https://ejemplo.com/avatar.png"),
                authenticationFor("alice-id"));

        assertThat(profile.username()).isEqualTo("alice_nueva");
        assertThat(profile.country()).isEqualTo("España");
        assertThat(profile.avatarUrl()).isEqualTo("https://ejemplo.com/avatar.png");
        assertThat(alice.getUsername()).isEqualTo("alice_nueva"); // se persistió de verdad en la entidad
    }

    @Test
    void updateProfileTreatsBlankCountryAndAvatarAsUnset() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("alice-id", "alice-id"))
                .thenReturn(List.of());

        UserProfileResponse profile = controller.updateProfile(
                "alice-id", new UpdateProfileRequest("alice", "   ", ""), authenticationFor("alice-id"));

        assertThat(profile.country()).isNull();
        assertThat(profile.avatarUrl()).isNull();
    }

    @Test
    void changePasswordRejectsEditingSomeoneElsesAccount() {
        assertThatThrownBy(() ->
                controller.changePassword("alice-id", new ChangePasswordRequest("x", "newpassword1"),
                        authenticationFor("bob-id")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void changePasswordRejectsAnIncorrectCurrentPassword() {
        User alice = new User("alice", passwordEncoder.encode("correcta123"));
        setId(alice, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() ->
                controller.changePassword("alice-id", new ChangePasswordRequest("incorrecta", "nuevapass123"),
                        authenticationFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void changePasswordRejectsATooShortNewPassword() {
        User alice = new User("alice", passwordEncoder.encode("correcta123"));
        setId(alice, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() ->
                controller.changePassword("alice-id", new ChangePasswordRequest("correcta123", "corta"),
                        authenticationFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void changePasswordSavesANewWorkingHash() {
        User alice = new User("alice", passwordEncoder.encode("correcta123"));
        setId(alice, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));

        controller.changePassword("alice-id", new ChangePasswordRequest("correcta123", "nuevapass456"),
                authenticationFor("alice-id"));

        assertThat(passwordEncoder.matches("nuevapass456", alice.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("correcta123", alice.getPasswordHash())).isFalse();
    }

    @Test
    void changePasswordRejectsSettingTheSamePasswordAgain() {
        User alice = new User("alice", passwordEncoder.encode("correcta123"));
        setId(alice, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() ->
                controller.changePassword("alice-id", new ChangePasswordRequest("correcta123", "correcta123"),
                        authenticationFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(userRepository, never()).save(alice);
    }

    @Test
    void changePasswordRejectsReusingAPasswordFromRecentHistory() {
        User alice = new User("alice", passwordEncoder.encode("original123"));
        setId(alice, "alice-id");
        // Ya cambiada una vez antes — "original123" queda en el historial, ya no es la actual.
        alice.changePassword(passwordEncoder.encode("segunda456"));
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() ->
                controller.changePassword("alice-id", new ChangePasswordRequest("segunda456", "original123"),
                        authenticationFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteAccountRejectsDeletingSomeoneElsesAccount() {
        assertThatThrownBy(() ->
                controller.deleteAccount("alice-id", new DeleteAccountRequest("x"), authenticationFor("bob-id")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void deleteAccountRejectsAnIncorrectPassword() {
        User alice = new User("alice", passwordEncoder.encode("correcta123"));
        setId(alice, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() ->
                controller.deleteAccount("alice-id", new DeleteAccountRequest("incorrecta"), authenticationFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(alice.isDeleted()).isFalse(); // nada se tocó tras el rechazo
    }

    @Test
    void deleteAccountAnonymizesTheUserInsteadOfRemovingTheRow() {
        User alice = new User("alice", passwordEncoder.encode("correcta123"));
        setId(alice, "alice-1234-5678-abcd");
        when(userRepository.findById("alice-1234-5678-abcd")).thenReturn(Optional.of(alice));

        controller.deleteAccount("alice-1234-5678-abcd", new DeleteAccountRequest("correcta123"),
                authenticationFor("alice-1234-5678-abcd"));

        assertThat(alice.isDeleted()).isTrue();
        assertThat(alice.getUsername()).isEqualTo("usuario-eliminado-alice-12"); // primeros 8 caracteres del id
        assertThat(alice.getCountry()).isNull();
        assertThat(alice.getAvatarUrl()).isNull();
        // Con la contraseña original ya no se puede entrar — la nueva es aleatoria e inaccesible.
        assertThat(passwordEncoder.matches("correcta123", alice.getPasswordHash())).isFalse();

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(alice);
    }

    @Test
    void deleteAccountDoesNotSaveAnythingWhenThePasswordIsWrong() {
        User alice = new User("alice", passwordEncoder.encode("correcta123"));
        setId(alice, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() ->
                controller.deleteAccount("alice-id", new DeleteAccountRequest("incorrecta"), authenticationFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class);

        verify(userRepository, never()).save(alice);
    }
}
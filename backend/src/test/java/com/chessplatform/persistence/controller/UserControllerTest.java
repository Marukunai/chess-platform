package com.chessplatform.persistence.controller;

import com.chessplatform.persistence.dto.LeaderboardEntryResponse;
import com.chessplatform.persistence.dto.UpdateProfileRequest;
import com.chessplatform.persistence.dto.UserProfileResponse;
import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.GameRepository;
import com.chessplatform.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GameRepository gameRepository;

    private UserController controller;

    @BeforeEach
    void setUp() {
        controller = new UserController(userRepository, gameRepository);
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
        alice.applyRatingUpdate(1800, 120, 0.06);
        User bob = new User("bob", "hash");
        bob.applyRatingUpdate(1600, 120, 0.06);
        when(userRepository.findTop50ByOrderByRatingDesc()).thenReturn(List.of(alice, bob));

        List<LeaderboardEntryResponse> leaderboard = controller.leaderboard();

        assertThat(leaderboard).hasSize(2);
        assertThat(leaderboard.get(0).rank()).isEqualTo(1);
        assertThat(leaderboard.get(0).username()).isEqualTo("alice");
        assertThat(leaderboard.get(0).rating()).isEqualTo(1800);
        assertThat(leaderboard.get(1).rank()).isEqualTo(2);
        assertThat(leaderboard.get(1).username()).isEqualTo("bob");
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
        viewer.applyRatingUpdate(1550, 140, 0.06);
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
        assertThat(profile.rating()).isEqualTo(1550);
        assertThat(profile.username()).isEqualTo("alice");
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
}
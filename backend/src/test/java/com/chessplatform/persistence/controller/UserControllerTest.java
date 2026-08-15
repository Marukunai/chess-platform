package com.chessplatform.persistence.controller;

import com.chessplatform.persistence.dto.LeaderboardEntryResponse;
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
}
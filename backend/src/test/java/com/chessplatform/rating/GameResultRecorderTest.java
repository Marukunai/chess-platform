package com.chessplatform.rating;

import com.chessplatform.engine.Move;
import com.chessplatform.engine.Square;
import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.GameRepository;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.realtime.GameSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameResultRecorderTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private GameRepository gameRepository;

    private GameResultRecorder recorder;

    @BeforeEach
    void setUp() {
        // GlickoRatingService real (no mock): es puro/barato de calcular, y usar el de
        // verdad da más confianza de que el cableado completo funciona, no solo que se
        // llamó a algo.
        recorder = new GameResultRecorder(userRepository, gameRepository, new GlickoRatingService());
    }

    private static GameSession newSession() {
        return new GameSession("white-id", "black-id", Duration.ofMinutes(5), Duration.ofSeconds(3));
    }

    @Test
    void recordUpdatesBothPlayersRatingsWhenWhiteWins() {
        User white = new User("white-user", "hash");
        User black = new User("black-user", "hash");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));

        recorder.record(newSession(), "1-0", "checkmate");

        ArgumentCaptor<User> savedUsers = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(savedUsers.capture());

        List<User> saved = savedUsers.getAllValues();
        assertThat(saved).contains(white, black);
        assertThat(white.getRating()).isGreaterThan(1500.0); // ganó, sube
        assertThat(black.getRating()).isLessThan(1500.0); // perdió, baja
    }

    @Test
    void recordSavesAGameWithTheCorrectResultAndPlayers() {
        User white = new User("white-user", "hash");
        User black = new User("black-user", "hash");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));

        recorder.record(newSession(), "0-1", "checkmate");

        ArgumentCaptor<Game> savedGame = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(savedGame.capture());

        assertThat(savedGame.getValue().getResult()).isEqualTo("0-1");
        assertThat(savedGame.getValue().getWhitePlayer()).isEqualTo(white);
        assertThat(savedGame.getValue().getBlackPlayer()).isEqualTo(black);
    }

    @Test
    void recordDoesNothingWhenAPlayerIsNotFound() {
        when(userRepository.findById("white-id")).thenReturn(Optional.empty());
        when(userRepository.findById("black-id")).thenReturn(Optional.of(new User("black-user", "hash")));

        Optional<GameResultRecorder.RatingChanges> result = recorder.record(newSession(), "1-0", "checkmate");

        assertThat(result).isEmpty();
        verify(userRepository, never()).save(any());
        verify(gameRepository, never()).save(any());
    }

    @Test
    void recordLeavesBothRatingsUnchangedOnADrawBetweenEquallyRatedPlayers() {
        User white = new User("white-user", "hash");
        User black = new User("black-user", "hash");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));

        recorder.record(newSession(), "1/2-1/2", "agreement");

        assertThat(white.getRating()).isCloseTo(1500.0, within(0.001));
        assertThat(black.getRating()).isCloseTo(1500.0, within(0.001));
    }

    @Test
    void recordSavesTheMoveListFromTheSessionsBoard() {
        User white = new User("white-user", "hash");
        User black = new User("black-user", "hash");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));

        GameSession session = newSession();
        session.applyMove(new Move(Square.of(4, 1), Square.of(4, 3))); // e2-e4
        session.applyMove(new Move(Square.of(4, 6), Square.of(4, 4))); // e7-e5

        recorder.record(session, "1-0", "checkmate");

        ArgumentCaptor<Game> savedGame = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(savedGame.capture());
        assertThat(savedGame.getValue().getMoveList()).isEqualTo("e2e4 e7e5");
    }

    @Test
    void recordReturnsTheRatingChangesItApplied() {
        User white = new User("white-user", "hash");
        User black = new User("black-user", "hash");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));

        Optional<GameResultRecorder.RatingChanges> result = recorder.record(newSession(), "1-0", "checkmate");

        assertThat(result).isPresent();
        assertThat(result.get().whiteChange()).isGreaterThan(0); // ganó, cambio positivo
        assertThat(result.get().blackChange()).isLessThan(0); // perdió, cambio negativo
        // El cambio de cada uno coincide con la diferencia real entre su rating final y
        // el inicial (1500 para los dos, ninguno había jugado antes).
        assertThat(result.get().whiteChange()).isCloseTo(white.getRating() - 1500.0, within(0.001));
        assertThat(result.get().blackChange()).isCloseTo(black.getRating() - 1500.0, within(0.001));
    }

    @Test
    void recordSavesTheRatingChangesOnTheGameItself() {
        User white = new User("white-user", "hash");
        User black = new User("black-user", "hash");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));

        recorder.record(newSession(), "1-0", "checkmate");

        ArgumentCaptor<Game> savedGame = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(savedGame.capture());
        assertThat(savedGame.getValue().getWhiteRatingChange()).isGreaterThan(0);
        assertThat(savedGame.getValue().getBlackRatingChange()).isLessThan(0);
    }

    @Test
    void recordSavesTheReasonOnTheGameItself() {
        User white = new User("white-user", "hash");
        User black = new User("black-user", "hash");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));

        recorder.record(newSession(), "0-1", "resignation");

        ArgumentCaptor<Game> savedGame = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(savedGame.capture());
        assertThat(savedGame.getValue().getReason()).isEqualTo("resignation");
    }
}
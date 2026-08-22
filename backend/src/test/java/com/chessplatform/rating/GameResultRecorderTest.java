package com.chessplatform.rating;

import com.chessplatform.engine.Move;
import com.chessplatform.engine.Square;
import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.entity.UserRating;
import com.chessplatform.persistence.repository.GameRepository;
import com.chessplatform.persistence.repository.UserRatingRepository;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.puzzle.PuzzleGenerationService;
import com.chessplatform.realtime.GameSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameResultRecorderTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRatingRepository userRatingRepository;

    @Mock
    private GameRepository gameRepository;

    @Mock
    private PuzzleGenerationService puzzleGenerationService;

    private GameResultRecorder recorder;

    @BeforeEach
    void setUp() {
        // UserRatingService real (no mock) sobre un UserRatingRepository sí mockeado —
        // es un envoltorio fino de verdad ("búscalo, si no existe créalo con los valores
        // por defecto"), así que usar el de verdad da más confianza de que el cableado
        // completo funciona, igual que ya se hacía con GlickoRatingService.
        UserRatingService userRatingService = new UserRatingService(userRatingRepository);
        recorder = new GameResultRecorder(userRepository, userRatingRepository, userRatingService,
                gameRepository, new GlickoRatingService(), puzzleGenerationService);
    }

    private static GameSession newSession() {
        // 5 min + 3 s == TimeControl.BLITZ — así que en todos estos tests la modalidad
        // afectada es siempre GameMode.BLITZ.
        return new GameSession("white-id", "black-id", Duration.ofMinutes(5), Duration.ofSeconds(3));
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

    /** Sin fila todavía para (usuario, modalidad) — UserRatingService.findOrDefault() devuelve una nueva sin guardar, con los valores por defecto de Glicko-2; el propio GameResultRecorder es quien la guarda después de actualizarla. */
    private void givenNoExistingRatingFor(String userId) {
        when(userRatingRepository.findByUser_IdAndMode(eq(userId), eq(GameMode.BLITZ))).thenReturn(Optional.empty());
        when(userRatingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordUpdatesBothPlayersRatingsWhenWhiteWins() {
        User white = new User("white-user", "hash");
        setId(white, "white-id");
        User black = new User("black-user", "hash");
        setId(black, "black-id");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));
        givenNoExistingRatingFor("white-id");
        givenNoExistingRatingFor("black-id");

        recorder.record(newSession(), "1-0", "checkmate");

        ArgumentCaptor<UserRating> savedRatings = ArgumentCaptor.forClass(UserRating.class);
        verify(userRatingRepository, org.mockito.Mockito.times(2)).save(savedRatings.capture());

        List<UserRating> saved = savedRatings.getAllValues();
        UserRating whiteRating = saved.stream().filter(r -> r.getUser() == white).findFirst().orElseThrow();
        UserRating blackRating = saved.stream().filter(r -> r.getUser() == black).findFirst().orElseThrow();
        assertThat(whiteRating.getRating()).isGreaterThan(1500.0); // ganó, sube
        assertThat(blackRating.getRating()).isLessThan(1500.0); // perdió, baja
    }

    @Test
    void recordSavesAGameWithTheCorrectResultAndPlayers() {
        User white = new User("white-user", "hash");
        setId(white, "white-id");
        User black = new User("black-user", "hash");
        setId(black, "black-id");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));
        givenNoExistingRatingFor("white-id");
        givenNoExistingRatingFor("black-id");

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
        verify(userRatingRepository, never()).save(any());
        verify(gameRepository, never()).save(any());
    }

    @Test
    void recordLeavesBothRatingsUnchangedOnADrawBetweenEquallyRatedPlayers() {
        User white = new User("white-user", "hash");
        setId(white, "white-id");
        User black = new User("black-user", "hash");
        setId(black, "black-id");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));
        givenNoExistingRatingFor("white-id");
        givenNoExistingRatingFor("black-id");

        recorder.record(newSession(), "1/2-1/2", "agreement");

        ArgumentCaptor<UserRating> savedRatings = ArgumentCaptor.forClass(UserRating.class);
        verify(userRatingRepository, org.mockito.Mockito.times(2)).save(savedRatings.capture());
        for (UserRating rating : savedRatings.getAllValues()) {
            assertThat(rating.getRating()).isCloseTo(1500.0, within(0.001));
        }
    }

    @Test
    void recordSavesTheMoveListFromTheSessionsBoard() {
        User white = new User("white-user", "hash");
        setId(white, "white-id");
        User black = new User("black-user", "hash");
        setId(black, "black-id");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));
        givenNoExistingRatingFor("white-id");
        givenNoExistingRatingFor("black-id");

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
        setId(white, "white-id");
        User black = new User("black-user", "hash");
        setId(black, "black-id");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));
        givenNoExistingRatingFor("white-id");
        givenNoExistingRatingFor("black-id");

        Optional<GameResultRecorder.RatingChanges> result = recorder.record(newSession(), "1-0", "checkmate");

        assertThat(result).isPresent();
        assertThat(result.get().whiteChange()).isGreaterThan(0); // ganó, cambio positivo
        assertThat(result.get().blackChange()).isLessThan(0); // perdió, cambio negativo
    }

    @Test
    void recordSavesTheRatingChangesOnTheGameItself() {
        User white = new User("white-user", "hash");
        setId(white, "white-id");
        User black = new User("black-user", "hash");
        setId(black, "black-id");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));
        givenNoExistingRatingFor("white-id");
        givenNoExistingRatingFor("black-id");

        recorder.record(newSession(), "1-0", "checkmate");

        ArgumentCaptor<Game> savedGame = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(savedGame.capture());
        assertThat(savedGame.getValue().getWhiteRatingChange()).isGreaterThan(0);
        assertThat(savedGame.getValue().getBlackRatingChange()).isLessThan(0);
    }

    @Test
    void recordSavesTheReasonOnTheGameItself() {
        User white = new User("white-user", "hash");
        setId(white, "white-id");
        User black = new User("black-user", "hash");
        setId(black, "black-id");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));
        givenNoExistingRatingFor("white-id");
        givenNoExistingRatingFor("black-id");

        recorder.record(newSession(), "0-1", "resignation");

        ArgumentCaptor<Game> savedGame = ArgumentCaptor.forClass(Game.class);
        verify(gameRepository).save(savedGame.capture());
        assertThat(savedGame.getValue().getReason()).isEqualTo("resignation");
    }

    @Test
    void recordUpdatesTheRatingForTheModeThatWasActuallyPlayedNotAnotherOne() {
        User white = new User("white-user", "hash");
        setId(white, "white-id");
        User black = new User("black-user", "hash");
        setId(black, "black-id");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));
        givenNoExistingRatingFor("white-id");
        givenNoExistingRatingFor("black-id");

        recorder.record(newSession(), "1-0", "checkmate"); // newSession() == BLITZ

        ArgumentCaptor<UserRating> savedRatings = ArgumentCaptor.forClass(UserRating.class);
        verify(userRatingRepository, org.mockito.Mockito.times(2)).save(savedRatings.capture());
        assertThat(savedRatings.getAllValues()).allMatch(rating -> rating.getMode() == GameMode.BLITZ);
    }

    @Test
    void recordDoesNotTouchTheOtherModesRatingForTheSamePlayers() {
        User white = new User("white-user", "hash");
        setId(white, "white-id");
        User black = new User("black-user", "hash");
        setId(black, "black-id");
        when(userRepository.findById("white-id")).thenReturn(Optional.of(white));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(black));
        givenNoExistingRatingFor("white-id");
        givenNoExistingRatingFor("black-id");

        recorder.record(newSession(), "1-0", "checkmate");

        // findByUser_IdAndMode solo se consulta para BLITZ, nunca para las otras tres —
        // no hay ningún motivo para que jugar un blitz toque nada de bullet/rápidas/clásicas.
        verify(userRatingRepository, never()).findByUser_IdAndMode(any(), eq(GameMode.BULLET));
        verify(userRatingRepository, never()).findByUser_IdAndMode(any(), eq(GameMode.RAPID));
        verify(userRatingRepository, never()).findByUser_IdAndMode(any(), eq(GameMode.CLASSICAL));
    }

    @Test
    void recordDoesNotTouchAnyRatingWhenTheOpponentIsABot() {
        User human = new User("alguien", "hash");
        setId(human, "white-id");
        User bot = new User("Stockfish (Fácil)", "hash");
        setId(bot, "black-id");
        bot.markAsBot();
        when(userRepository.findById("white-id")).thenReturn(Optional.of(human));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(bot));

        recorder.record(newSession(), "1-0", "checkmate");

        verify(userRatingRepository, never()).findByUser_IdAndMode(any(), any());
        verify(userRatingRepository, never()).save(any());
    }

    @Test
    void recordStillSavesTheGameWhenTheOpponentIsABot() {
        User human = new User("alguien", "hash");
        setId(human, "white-id");
        User bot = new User("Stockfish (Fácil)", "hash");
        setId(bot, "black-id");
        bot.markAsBot();
        when(userRepository.findById("white-id")).thenReturn(Optional.of(human));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(bot));

        // Aunque sea contra un bot, la partida se guarda igual — quien juega contra el
        // bot puede reproducirla después, como cualquier otra partida de su historial.
        recorder.record(newSession(), "1-0", "checkmate");

        verify(gameRepository).save(any());
    }

    @Test
    void recordReturnsZeroRatingChangesForAGameAgainstABot() {
        User human = new User("alguien", "hash");
        setId(human, "white-id");
        User bot = new User("Stockfish (Fácil)", "hash");
        setId(bot, "black-id");
        bot.markAsBot();
        when(userRepository.findById("white-id")).thenReturn(Optional.of(human));
        when(userRepository.findById("black-id")).thenReturn(Optional.of(bot));

        var result = recorder.record(newSession(), "1-0", "checkmate");

        assertThat(result).isPresent();
        assertThat(result.get().whiteChange()).isZero();
        assertThat(result.get().blackChange()).isZero();
    }
}
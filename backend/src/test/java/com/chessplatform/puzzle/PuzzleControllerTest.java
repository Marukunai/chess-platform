package com.chessplatform.puzzle;

import com.chessplatform.persistence.entity.Puzzle;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.entity.UserPuzzleRating;
import com.chessplatform.persistence.repository.PuzzleRepository;
import com.chessplatform.persistence.repository.UserPuzzleAttemptRepository;
import com.chessplatform.persistence.repository.UserPuzzleRatingRepository;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.puzzle.dto.PuzzleAttemptRequest;
import com.chessplatform.puzzle.dto.PuzzleAttemptResponse;
import com.chessplatform.puzzle.dto.PuzzleResponse;
import com.chessplatform.rating.GlickoRatingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PuzzleControllerTest {

    @Mock
    private PuzzleRepository puzzleRepository;

    @Mock
    private UserPuzzleAttemptRepository attemptRepository;

    @Mock
    private UserPuzzleRatingRepository userPuzzleRatingRepository;

    @Mock
    private UserRepository userRepository;

    private UserPuzzleRatingService userPuzzleRatingService;
    private PuzzleController controller;

    @BeforeEach
    void setUp() {
        // UserPuzzleRatingService real (no mock), mismo motivo de siempre: es un
        // envoltorio fino sobre el repositorio, usar el de verdad da más confianza de
        // que el cableado completo funciona.
        userPuzzleRatingService = new UserPuzzleRatingService(userPuzzleRatingRepository);
        controller = new PuzzleController(puzzleRepository, attemptRepository, userPuzzleRatingRepository,
                userPuzzleRatingService, userRepository, new GlickoRatingService());
    }

    private static Authentication authFor(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
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

    private static void setPuzzleId(Puzzle puzzle, String id) {
        try {
            Field field = Puzzle.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(puzzle, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void nextThrowsUnauthorizedWhenNotAuthenticated() {
        assertThatThrownBy(() -> controller.next(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("iniciar sesión");
    }

    @Test
    void nextThrowsNotFoundWhenNoPuzzlesAreAvailable() {
        User user = new User("alice", "hash");
        setId(user, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        when(userPuzzleRatingRepository.findByUser_Id("alice-id")).thenReturn(Optional.empty());
        when(puzzleRepository.findClosestByRatingExcludingAttemptedByUser(eq("alice-id"), anyDouble()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> controller.next(authFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void nextReturnsTheClosestPuzzleByRating() {
        User user = new User("alice", "hash");
        setId(user, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        when(userPuzzleRatingRepository.findByUser_Id("alice-id")).thenReturn(Optional.empty()); // 1500 por defecto
        Puzzle puzzle = new Puzzle("game-id", "algún-fen", "white", "e2e4", "e2e4 d2d4");
        setPuzzleId(puzzle, "puzzle-id");
        when(puzzleRepository.findClosestByRatingExcludingAttemptedByUser(eq("alice-id"), anyDouble()))
                .thenReturn(List.of(puzzle));

        PuzzleResponse response = controller.next(authFor("alice-id"));

        assertThat(response.puzzleId()).isEqualTo("puzzle-id");
        assertThat(response.fen()).isEqualTo("algún-fen");
        assertThat(response.sideToMove()).isEqualTo("white");
        assertThat(response.legalMovesUci()).containsExactly("e2e4", "d2d4");
    }

    @Test
    void attemptThrowsNotFoundForAnUnknownPuzzle() {
        User user = new User("alice", "hash");
        setId(user, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        when(puzzleRepository.findById("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.attempt("no-existe", new PuzzleAttemptRequest("e2e4"), authFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void attemptThrowsConflictWhenTheUserAlreadyAttemptedThisPuzzle() {
        User user = new User("alice", "hash");
        setId(user, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        Puzzle puzzle = new Puzzle("game-id", "fen", "white", "e2e4", "e2e4 d2d4");
        setPuzzleId(puzzle, "puzzle-id");
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(true);

        assertThatThrownBy(() -> controller.attempt("puzzle-id", new PuzzleAttemptRequest("e2e4"), authFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void attemptMarksACorrectMoveAsCorrectAndIncreasesTheUsersRating() {
        User user = new User("alice", "hash");
        setId(user, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        Puzzle puzzle = new Puzzle("game-id", "fen", "white", "e2e4", "e2e4 d2d4");
        setPuzzleId(puzzle, "puzzle-id");
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);
        when(userPuzzleRatingRepository.findByUser_Id("alice-id")).thenReturn(Optional.empty());

        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("e2e4"), authFor("alice-id"));

        assertThat(response.correct()).isTrue();
        assertThat(response.ratingChange()).isPositive();
        assertThat(response.newRating()).isGreaterThan(1500);
    }

    @Test
    void attemptMarksAWrongMoveAsIncorrectAndDecreasesTheUsersRating() {
        User user = new User("alice", "hash");
        setId(user, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        Puzzle puzzle = new Puzzle("game-id", "fen", "white", "e2e4", "e2e4 d2d4");
        setPuzzleId(puzzle, "puzzle-id");
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);
        when(userPuzzleRatingRepository.findByUser_Id("alice-id")).thenReturn(Optional.empty());

        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("d2d4"), authFor("alice-id"));

        assertThat(response.correct()).isFalse();
        assertThat(response.ratingChange()).isNegative();
        assertThat(response.newRating()).isLessThan(1500);
    }

    @Test
    void attemptAlwaysReturnsTheSolutionRegardlessOfWhetherItWasCorrect() {
        User user = new User("alice", "hash");
        setId(user, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        Puzzle puzzle = new Puzzle("game-id", "fen", "white", "e2e4", "e2e4 d2d4");
        setPuzzleId(puzzle, "puzzle-id");
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);
        when(userPuzzleRatingRepository.findByUser_Id("alice-id")).thenReturn(Optional.empty());

        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("una-jugada-cualquiera"), authFor("alice-id"));

        assertThat(response.solutionUci()).isEqualTo("e2e4");
    }

    @Test
    void attemptRecordsTheAttemptSoItIsNotOfferedAgain() {
        User user = new User("alice", "hash");
        setId(user, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        Puzzle puzzle = new Puzzle("game-id", "fen", "white", "e2e4", "e2e4 d2d4");
        setPuzzleId(puzzle, "puzzle-id");
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);
        when(userPuzzleRatingRepository.findByUser_Id("alice-id")).thenReturn(Optional.empty());

        controller.attempt("puzzle-id", new PuzzleAttemptRequest("e2e4"), authFor("alice-id"));

        verify(attemptRepository).save(any());
    }

    @Test
    void attemptComparesTheMoveIgnoringCase() {
        User user = new User("alice", "hash");
        setId(user, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        Puzzle puzzle = new Puzzle("game-id", "fen", "white", "e2e4", "e2e4 d2d4");
        setPuzzleId(puzzle, "puzzle-id");
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);
        when(userPuzzleRatingRepository.findByUser_Id("alice-id")).thenReturn(Optional.empty());

        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("E2E4"), authFor("alice-id"));

        assertThat(response.correct()).isTrue();
    }

    @Test
    void nextUsesTheUsersExistingPuzzleRatingWhenTheyHaveOne() {
        User user = new User("alice", "hash");
        setId(user, "alice-id");
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        UserPuzzleRating existingRating = new UserPuzzleRating(user);
        existingRating.applyRatingUpdate(1800, 100, 0.05);
        when(userPuzzleRatingRepository.findByUser_Id("alice-id")).thenReturn(Optional.of(existingRating));
        when(puzzleRepository.findClosestByRatingExcludingAttemptedByUser(eq("alice-id"), anyDouble()))
                .thenReturn(List.of(new Puzzle("game-id", "fen", "white", "e2e4", "e2e4 d2d4")));

        controller.next(authFor("alice-id"));

        verify(puzzleRepository).findClosestByRatingExcludingAttemptedByUser("alice-id", 1800.0);
    }
}
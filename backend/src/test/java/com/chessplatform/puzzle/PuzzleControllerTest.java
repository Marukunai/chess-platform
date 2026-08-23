package com.chessplatform.puzzle;

import com.chessplatform.achievement.AchievementUnlockService;
import com.chessplatform.persistence.dto.LeaderboardEntryResponse;
import com.chessplatform.persistence.entity.Puzzle;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.entity.UserPuzzleRating;
import com.chessplatform.persistence.repository.PuzzleRepository;
import com.chessplatform.persistence.repository.UserPuzzleAttemptRepository;
import com.chessplatform.persistence.repository.UserPuzzleRatingRepository;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.puzzle.dto.PuzzleAttemptRequest;
import com.chessplatform.puzzle.dto.PuzzleAttemptResponse;
import com.chessplatform.puzzle.dto.PuzzleHintResponse;
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
import java.security.Principal;
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

    @Mock
    private AchievementUnlockService achievementUnlockService;

    private UserPuzzleRatingService userPuzzleRatingService;
    private PuzzleController controller;

    @BeforeEach
    void setUp() {
        userPuzzleRatingService = new UserPuzzleRatingService(userPuzzleRatingRepository);
        controller = new PuzzleController(puzzleRepository, attemptRepository, userPuzzleRatingRepository,
                userPuzzleRatingService, userRepository, new GlickoRatingService(), achievementUnlockService);
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

    /** Puzzle de una sola jugada — posición inicial, solución "e2e4" (siempre legal). */
    private static Puzzle singleMovePuzzle() {
        Puzzle puzzle = new Puzzle("game-id", "fen", "white", "e2e4", "e2e4 d2d4", "");
        setPuzzleId(puzzle, "puzzle-id");
        return puzzle;
    }

    /** Puzzle de dos jugadas — posición inicial, línea "e2e4 e7e5 g1f3" (las tres, legales de verdad en orden). */
    private static Puzzle twoMovePuzzle() {
        Puzzle puzzle = new Puzzle("game-id", "fen", "white", "e2e4 e7e5 g1f3", "e2e4 d2d4", "");
        setPuzzleId(puzzle, "puzzle-id");
        return puzzle;
    }

    private static User aliceWithNoExistingRating(UserPuzzleRatingRepository repo) {
        User user = aliceUser();
        when(repo.findByUser_Id("alice-id")).thenReturn(Optional.empty());
        return user;
    }

    /** Un User con el id ya puesto ("alice-id") — a diferencia de "new User(...)" a secas, que se queda con id=null hasta que se persiste de verdad. */
    private static User aliceUser() {
        User user = new User("alice", "hash");
        setId(user, "alice-id");
        return user;
    }

    // ---- /next ----

    @Test
    void nextThrowsUnauthorizedWhenNotAuthenticated() {
        assertThatThrownBy(() -> controller.next(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("iniciar sesión");
    }

    @Test
    void nextThrowsNotFoundWhenNoPuzzlesAreAvailable() {
        User user = aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        when(puzzleRepository.findClosestByRatingExcludingAttemptedByUser(eq("alice-id"), anyDouble()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> controller.next(authFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void nextReturnsTheClosestPuzzleByRating() {
        User user = aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        Puzzle puzzle = singleMovePuzzle();
        when(puzzleRepository.findClosestByRatingExcludingAttemptedByUser(eq("alice-id"), anyDouble()))
                .thenReturn(List.of(puzzle));

        PuzzleResponse response = controller.next(authFor("alice-id"));

        assertThat(response.puzzleId()).isEqualTo("puzzle-id");
        assertThat(response.fen()).isEqualTo("fen");
        assertThat(response.sideToMove()).isEqualTo("white");
        assertThat(response.legalMovesUci()).containsExactly("e2e4", "d2d4");
    }

    @Test
    void nextReturnsNullPreviousFenWhenThePuzzleIsTheInitialPosition() {
        User user = aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        Puzzle puzzle = singleMovePuzzle(); // movesUpToPosition = "" -> el puzzle ES la posición inicial
        when(puzzleRepository.findClosestByRatingExcludingAttemptedByUser(eq("alice-id"), anyDouble()))
                .thenReturn(List.of(puzzle));

        PuzzleResponse response = controller.next(authFor("alice-id"));

        assertThat(response.previousFen()).isNull();
        assertThat(response.previousMoveUci()).isNull();
    }

    @Test
    void nextReturnsThePositionBeforeTheBlunderWhenThereIsOne() {
        User user = aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        // movesUpToPosition = "e2e4 e7e5" -> el puzzle está DOS jugadas después del
        // inicio; la "jugada anterior" debería ser e7e5, y previousFen la posición
        // justo tras e2e4 (una jugada antes de eso).
        Puzzle puzzle = new Puzzle("game-id", "fen-del-puzzle", "white", "g1f3", "g1f3", "e2e4 e7e5");
        setPuzzleId(puzzle, "puzzle-id");
        when(puzzleRepository.findClosestByRatingExcludingAttemptedByUser(eq("alice-id"), anyDouble()))
                .thenReturn(List.of(puzzle));

        PuzzleResponse response = controller.next(authFor("alice-id"));

        assertThat(response.previousMoveUci()).isEqualTo("e7e5");
        assertThat(response.previousFen()).contains("4P3"); // el peón blanco ya está en e4, negras por mover (justo antes de e7e5)
    }

    // ---- /{puzzleId}/hint ----

    @Test
    void hintReturnsTheOriginSquareOfTheExpectedMove() {
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(aliceUser()));
        Puzzle puzzle = singleMovePuzzle();
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));

        PuzzleHintResponse hint = controller.hint("puzzle-id", 0, authFor("alice-id"));

        assertThat(hint.originSquare()).isEqualTo("e2");
    }

    @Test
    void hintForTheSecondStepOfAMultiMovePuzzle() {
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(aliceUser()));
        Puzzle puzzle = twoMovePuzzle(); // "e2e4 e7e5 g1f3" -> segundo paso del que resuelve es "g1f3", en el índice 2 de la línea
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));

        PuzzleHintResponse hint = controller.hint("puzzle-id", 1, authFor("alice-id"));

        assertThat(hint.originSquare()).isEqualTo("g1");
    }

    @Test
    void hintThrowsBadRequestForAStepOutOfRange() {
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(aliceUser()));
        Puzzle puzzle = singleMovePuzzle(); // solo tiene un paso (índice 0)
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));

        assertThatThrownBy(() -> controller.hint("puzzle-id", 1, authFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ---- /{puzzleId}/attempt — puzzles de una sola jugada (comportamiento ya existente) ----

    @Test
    void attemptThrowsNotFoundForAnUnknownPuzzle() {
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(aliceUser()));
        when(puzzleRepository.findById("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.attempt("no-existe", new PuzzleAttemptRequest("e2e4", 0, false), authFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void attemptThrowsConflictWhenTheUserAlreadyAttemptedThisPuzzle() {
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(aliceUser()));
        Puzzle puzzle = singleMovePuzzle();
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(true);

        assertThatThrownBy(() -> controller.attempt("puzzle-id", new PuzzleAttemptRequest("e2e4", 0, false), authFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class);
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void attemptThrowsBadRequestForAStepOutOfRange() {
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(aliceUser()));
        Puzzle puzzle = singleMovePuzzle();
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);

        assertThatThrownBy(() -> controller.attempt("puzzle-id", new PuzzleAttemptRequest("e2e4", 1, false), authFor("alice-id")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void attemptOnASingleMovePuzzleClosesImmediatelyWhenCorrect() {
        User user = aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        Puzzle puzzle = singleMovePuzzle();
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);

        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("e2e4", 0, false), authFor("alice-id"));

        assertThat(response.correct()).isTrue();
        assertThat(response.done()).isTrue();
        assertThat(response.opponentReplyUci()).isNull();
        assertThat(response.legalMovesUci()).isNull(); // intento cerrado, ya no hay siguiente paso que jugar
        assertThat(response.ratingChange()).isPositive();
        assertThat(response.newRating()).isGreaterThan(1500);
    }

    @Test
    void attemptOnASingleMovePuzzleClosesImmediatelyWhenIncorrect() {
        User user = aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        Puzzle puzzle = singleMovePuzzle();
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);

        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("d2d4", 0, false), authFor("alice-id"));

        assertThat(response.correct()).isFalse();
        assertThat(response.done()).isTrue();
        assertThat(response.ratingChange()).isNegative();
        assertThat(response.solutionUci()).isEqualTo("e2e4");
    }

    @Test
    void attemptComparesTheMoveIgnoringCase() {
        aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(aliceUser()));
        Puzzle puzzle = singleMovePuzzle();
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);

        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("E2E4", 0, false), authFor("alice-id"));

        assertThat(response.correct()).isTrue();
    }

    @Test
    void attemptRecordsTheAttemptSoItIsNotOfferedAgain() {
        aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(aliceUser()));
        Puzzle puzzle = singleMovePuzzle();
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);

        controller.attempt("puzzle-id", new PuzzleAttemptRequest("e2e4", 0, false), authFor("alice-id"));

        verify(attemptRepository).save(any());
    }

    @Test
    void attemptChecksAchievementsAfterClosing() {
        aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(aliceUser()));
        Puzzle puzzle = singleMovePuzzle();
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);

        controller.attempt("puzzle-id", new PuzzleAttemptRequest("e2e4", 0, false), authFor("alice-id"));

        verify(achievementUnlockService).checkAndNotify("alice-id");
    }

    @Test
    void attemptReturnsThePositionAfterTheFullSolutionLineEvenWhenWrong() {
        aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(aliceUser()));
        Puzzle puzzle = singleMovePuzzle();
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);

        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("d2d4", 0, false), authFor("alice-id"));

        assertThat(response.resultingFen()).contains("4P3"); // la posición de la SOLUCIÓN (e2e4), no la del intento fallido (d2d4)
    }

    @Test
    void attemptWithHintUsedGivesPartialCreditEvenWhenFullyCorrect() {
        aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(aliceUser()));
        Puzzle puzzle = singleMovePuzzle();
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);

        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("e2e4", 0, true), authFor("alice-id"));

        assertThat(response.correct()).isTrue(); // el paso en sí fue correcto...
        assertThat(response.ratingChange()).isZero(); // ...pero con pista cuenta como tablas, no como victoria (rating sin cambios)
    }

    // ---- /{puzzleId}/attempt — puzzles de varias jugadas ----

    @Test
    void attemptOnAMultiMovePuzzleDoesNotCloseAfterACorrectNonFinalStep() {
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(aliceUser()));
        Puzzle puzzle = twoMovePuzzle();
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);

        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("e2e4", 0, false), authFor("alice-id"));

        assertThat(response.correct()).isTrue();
        assertThat(response.done()).isFalse();
        assertThat(response.opponentReplyUci()).isEqualTo("e7e5"); // la respuesta forzada, para animarla
        assertThat(response.legalMovesUci()).isNotEmpty(); // jugadas legales de la posición ya con la respuesta del rival aplicada, para poder intentar el siguiente paso
        assertThat(response.newRating()).isNull(); // el intento sigue abierto, no hay rating todavía
        verify(attemptRepository, never()).save(any()); // tampoco se registra como cerrado
        verify(userPuzzleRatingRepository, never()).save(any());
    }

    @Test
    void attemptOnAMultiMovePuzzleClosesWithFullCreditWhenBothStepsAreCorrect() {
        User user = aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        Puzzle puzzle = twoMovePuzzle();
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);

        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("g1f3", 1, false), authFor("alice-id"));

        assertThat(response.correct()).isTrue();
        assertThat(response.done()).isTrue();
        assertThat(response.ratingChange()).isPositive(); // acierto completo, sin pista -> victoria de verdad
    }

    @Test
    void attemptReturnsAPositionForEachMoveOfTheSolutionLineForNavigation() {
        User user = aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        Puzzle puzzle = twoMovePuzzle(); // "e2e4 e7e5 g1f3" -> 3 jugadas en la línea
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);

        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("g1f3", 1, false), authFor("alice-id"));

        // 4 posiciones: la del propio puzzle (sin ninguna jugada de la línea todavía)
        // más una por cada una de las 3 jugadas de la línea.
        assertThat(response.solutionFenSequence()).hasSize(4);
        assertThat(response.solutionFenSequence().get(0)).startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w"); // posición inicial, sin jugar nada de la línea
        assertThat(response.solutionFenSequence().get(1)).contains("4P3"); // tras e2e4
        assertThat(response.solutionFenSequence().get(3)).isEqualTo(response.resultingFen()); // la última coincide con la posición final
    }

    @Test
    void attemptReturnsReadableNotationForTheSolutionLineNotRawUci() {
        User user = aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        Puzzle puzzle = twoMovePuzzle(); // "e2e4 e7e5 g1f3" -> "e4", "e5", "Nf3" en notación legible
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);

        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("g1f3", 1, false), authFor("alice-id"));

        assertThat(response.solutionNotation()).containsExactly("e4", "e5", "Nf3");
    }

    @Test
    void attemptDoesNotReturnASolutionFenSequenceWhileTheAttemptIsStillOpen() {
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(aliceUser()));
        Puzzle puzzle = twoMovePuzzle();
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);

        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("e2e4", 0, false), authFor("alice-id"));

        assertThat(response.done()).isFalse();
        assertThat(response.solutionFenSequence()).isNull(); // todavía no hay nada definitivo que enseñar
    }

    @Test
    void attemptOnAMultiMovePuzzleGivesPartialCreditWhenFailingAfterAnEarlierCorrectStep() {
        User user = aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        Puzzle puzzle = twoMovePuzzle();
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);

        // Se salta directamente al segundo paso con una jugada equivocada, simulando
        // que el primero (e2e4) ya se acertó antes (en una petición previa) — el
        // controlador no necesita rehacer el primer paso, solo confía en stepIndex.
        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("una-jugada-cualquiera", 1, false), authFor("alice-id"));

        assertThat(response.correct()).isFalse();
        assertThat(response.done()).isTrue();
        // Ni tan malo como fallar el primer paso (crédito parcial: se acertó uno de
        // los dos) ni tan bueno como acertar los dos — tablas, sin cambio de rating.
        assertThat(response.ratingChange()).isZero();
    }

    @Test
    void attemptOnAMultiMovePuzzleGivesFullLossWhenFailingTheFirstStep() {
        User user = aliceWithNoExistingRating(userPuzzleRatingRepository);
        when(userRepository.findById("alice-id")).thenReturn(Optional.of(user));
        Puzzle puzzle = twoMovePuzzle();
        when(puzzleRepository.findById("puzzle-id")).thenReturn(Optional.of(puzzle));
        when(attemptRepository.existsByUser_IdAndPuzzle_Id("alice-id", "puzzle-id")).thenReturn(false);

        PuzzleAttemptResponse response = controller.attempt("puzzle-id", new PuzzleAttemptRequest("d2d4", 0, false), authFor("alice-id"));

        assertThat(response.correct()).isFalse();
        assertThat(response.done()).isTrue();
        assertThat(response.ratingChange()).isNegative(); // ningún paso acertado -> derrota completa, no tablas
    }

    // ---- /leaderboard (sin cambios de comportamiento, se conserva la cobertura) ----

    @Test
    void leaderboardReturnsEntriesRankedByRatingDescending() {
        User alice = new User("alice", "hash");
        setId(alice, "alice-id");
        User bob = new User("bob", "hash");
        setId(bob, "bob-id");
        UserPuzzleRating aliceRating = new UserPuzzleRating(alice);
        aliceRating.applyRatingUpdate(2100, 80, 0.05);
        UserPuzzleRating bobRating = new UserPuzzleRating(bob);
        bobRating.applyRatingUpdate(1750, 100, 0.05);
        when(userPuzzleRatingRepository.findTop50ByUser_DeletedAtIsNullAndUser_BotFalseOrderByRatingDesc())
                .thenReturn(List.of(aliceRating, bobRating));

        List<LeaderboardEntryResponse> leaderboard = controller.leaderboard();

        assertThat(leaderboard).hasSize(2);
        assertThat(leaderboard.get(0).rank()).isEqualTo(1);
        assertThat(leaderboard.get(0).username()).isEqualTo("alice");
        assertThat(leaderboard.get(1).rank()).isEqualTo(2);
    }

    @Test
    void leaderboardReturnsAnEmptyListWhenNobodyHasSolvedAPuzzleYet() {
        when(userPuzzleRatingRepository.findTop50ByUser_DeletedAtIsNullAndUser_BotFalseOrderByRatingDesc())
                .thenReturn(List.of());

        assertThat(controller.leaderboard()).isEmpty();
    }
}
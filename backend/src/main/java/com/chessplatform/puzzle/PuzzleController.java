package com.chessplatform.puzzle;

import com.chessplatform.achievement.AchievementUnlockService;
import com.chessplatform.persistence.entity.Puzzle;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.entity.UserPuzzleAttempt;
import com.chessplatform.persistence.entity.UserPuzzleRating;
import com.chessplatform.persistence.repository.PuzzleRepository;
import com.chessplatform.persistence.repository.UserPuzzleAttemptRepository;
import com.chessplatform.persistence.repository.UserPuzzleRatingRepository;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.puzzle.dto.PuzzleAttemptRequest;
import com.chessplatform.puzzle.dto.PuzzleAttemptResponse;
import com.chessplatform.puzzle.dto.PuzzleResponse;
import com.chessplatform.rating.GlickoRatingService;
import com.chessplatform.rating.GlickoRatingService.Outcome;
import com.chessplatform.rating.GlickoRatingService.RatingResult;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Resolver puzzles — a diferencia del resto de la plataforma (partidas, chat...), esto
 * es REST normal, no STOMP: no hace falta tiempo real (nadie más está mirando el mismo
 * puzzle a la vez), así que no compensa la complejidad de un canal en vivo para esto.
 * Requiere identidad en los dos endpoints — a diferencia del perfil o el ranking, aquí
 * "quién eres" determina QUÉ te toca ver (según tu rating) y actualiza TU rating con
 * cada intento, así que no tiene sentido de forma anónima.
 */
@RestController
@RequestMapping("/api/puzzles")
public class PuzzleController {

    private final PuzzleRepository puzzleRepository;
    private final UserPuzzleAttemptRepository attemptRepository;
    private final UserPuzzleRatingRepository userPuzzleRatingRepository;
    private final UserPuzzleRatingService userPuzzleRatingService;
    private final UserRepository userRepository;
    private final GlickoRatingService ratingService;
    private final AchievementUnlockService achievementUnlockService;

    public PuzzleController(PuzzleRepository puzzleRepository, UserPuzzleAttemptRepository attemptRepository,
                            UserPuzzleRatingRepository userPuzzleRatingRepository,
                            UserPuzzleRatingService userPuzzleRatingService, UserRepository userRepository,
                            GlickoRatingService ratingService, AchievementUnlockService achievementUnlockService) {
        this.puzzleRepository = puzzleRepository;
        this.attemptRepository = attemptRepository;
        this.userPuzzleRatingRepository = userPuzzleRatingRepository;
        this.userPuzzleRatingService = userPuzzleRatingService;
        this.userRepository = userRepository;
        this.ratingService = ratingService;
        this.achievementUnlockService = achievementUnlockService;
    }

    @GetMapping("/next")
    public PuzzleResponse next(Authentication authentication) {
        User user = requireUser(authentication);
        UserPuzzleRating userRating = userPuzzleRatingService.findOrDefault(user);

        List<Puzzle> candidates = puzzleRepository.findClosestByRatingExcludingAttemptedByUser(
                user.getId(), userRating.getRating());
        if (candidates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No hay puzzles disponibles ahora mismo — vuelve a intentarlo cuando se hayan jugado más partidas");
        }

        Puzzle puzzle = candidates.get(0);
        List<String> legalMoves = List.of(puzzle.getLegalMovesUci().split(" "));
        return new PuzzleResponse(puzzle.getId(), puzzle.getFen(), puzzle.getSideToMove(),
                (int) Math.round(puzzle.getRating()), legalMoves);
    }

    @PostMapping("/{puzzleId}/attempt")
    public PuzzleAttemptResponse attempt(@PathVariable String puzzleId, @RequestBody PuzzleAttemptRequest request,
                                         Authentication authentication) {
        User user = requireUser(authentication);
        Puzzle puzzle = puzzleRepository.findById(puzzleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Puzzle no encontrado"));

        if (attemptRepository.existsByUser_IdAndPuzzle_Id(user.getId(), puzzleId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya intentaste este puzzle antes");
        }

        String submittedMove = request.moveUci() == null ? "" : request.moveUci().trim();
        boolean correct = submittedMove.equalsIgnoreCase(puzzle.getSolutionUci());

        UserPuzzleRating userRating = userPuzzleRatingService.findOrDefault(user);

        // El intento se trata como una partida de una jugada entre el usuario y el
        // propio puzzle — reutiliza GlickoRatingService tal cual, sin ningún cálculo
        // nuevo. Si aciertas, "ganas" al puzzle (tu rating sube, el suyo baja un poco —
        // resulta que no era tan difícil); si fallas, al revés.
        RatingResult userBefore = new RatingResult(userRating.getRating(), userRating.getRatingDeviation(), userRating.getVolatility());
        RatingResult puzzleBefore = new RatingResult(puzzle.getRating(), puzzle.getRatingDeviation(), puzzle.getVolatility());

        RatingResult userAfter = ratingService.updateRating(userBefore, puzzleBefore, correct ? Outcome.WIN : Outcome.LOSS);
        RatingResult puzzleAfter = ratingService.updateRating(puzzleBefore, userBefore, correct ? Outcome.LOSS : Outcome.WIN);

        userRating.applyRatingUpdate(userAfter.rating(), userAfter.ratingDeviation(), userAfter.volatility());
        puzzle.applyRatingUpdate(puzzleAfter.rating(), puzzleAfter.ratingDeviation(), puzzleAfter.volatility());
        userPuzzleRatingRepository.save(userRating);
        puzzleRepository.save(puzzle);

        attemptRepository.save(new UserPuzzleAttempt(user, puzzle, correct));

        // Al final de todo, con el intento ya registrado y el rating ya actualizado —
        // igual que GameEndNotifier lo hace al terminar una partida, solo que aquí
        // hace falta llamarlo explícitamente porque resolver un puzzle no pasa por
        // GameEndNotifier en absoluto (es un sistema completamente aparte).
        achievementUnlockService.checkAndNotify(user.getId());

        int ratingChange = (int) Math.round(userAfter.rating() - userBefore.rating());
        return new PuzzleAttemptResponse(correct, puzzle.getSolutionUci(), (int) Math.round(userAfter.rating()), ratingChange);
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Hace falta iniciar sesión");
        }
        return userRepository.findById(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }
}
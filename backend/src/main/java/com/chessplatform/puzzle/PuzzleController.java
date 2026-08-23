package com.chessplatform.puzzle;

import com.chessplatform.achievement.AchievementUnlockService;
import com.chessplatform.engine.Board;
import com.chessplatform.engine.Move;
import com.chessplatform.persistence.dto.LeaderboardEntryResponse;
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
import com.chessplatform.puzzle.dto.PuzzleHintResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Resolver puzzles — a diferencia del resto de la plataforma (partidas, chat...), esto
 * es REST normal, no STOMP: no hace falta tiempo real (nadie más está mirando el mismo
 * puzzle a la vez), así que no compensa la complejidad de un canal en vivo para esto.
 * Requiere identidad en todos los endpoints salvo el ranking — a diferencia del perfil,
 * aquí "quién eres" determina QUÉ te toca ver (según tu rating) y actualiza TU rating
 * con cada intento, así que no tiene sentido de forma anónima.
 *
 * Los puzzles pueden ser de una jugada o de varias (ver PuzzleGenerationService) — el
 * flujo de intento es el mismo para los dos: se envía un paso a la vez (stepIndex
 * 0-indexado), y solo se cierra el intento (done=true, con rating actualizado) al
 * fallar algún paso o al acertar el último. Un puzzle de una sola jugada es,
 * simplemente, uno donde el primer paso YA es el último.
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

    /**
     * Ranking por rating de puzzles — público, a diferencia del resto de endpoints de
     * aquí (aquí solo se está leyendo, mismo criterio que el ranking de rating de
     * partidas). Ver SecurityConfig, que lo añade explícitamente al permitAll en vez de
     * abrir todo /api/puzzles/**.
     */
    @GetMapping("/leaderboard")
    public List<LeaderboardEntryResponse> leaderboard() {
        List<UserPuzzleRating> topRatings = userPuzzleRatingRepository.findTop50ByUser_DeletedAtIsNullAndUser_BotFalseOrderByRatingDesc();
        return IntStream.range(0, topRatings.size())
                .mapToObj(i -> {
                    UserPuzzleRating rating = topRatings.get(i);
                    return new LeaderboardEntryResponse(i + 1, rating.getUser().getId(), rating.getUser().getUsername(),
                            (int) Math.round(rating.getRating()));
                })
                .toList();
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

        // La posición justo antes del error que originó el puzzle, y la jugada que
        // llevó hasta aquí — para que el cliente pueda animarla al abrir el puzzle.
        String previousFen = null;
        String previousMoveUci = null;
        String movesUpToPosition = puzzle.getMovesUpToPosition();
        if (movesUpToPosition != null && !movesUpToPosition.isBlank()) {
            String[] setupMoves = movesUpToPosition.trim().split(" ");
            previousMoveUci = setupMoves[setupMoves.length - 1];
            Board board = Board.initial();
            for (int i = 0; i < setupMoves.length - 1; i++) {
                board.applyMove(Move.fromUci(setupMoves[i]));
            }
            previousFen = board.toFen();
        }

        return new PuzzleResponse(puzzle.getId(), puzzle.getFen(), puzzle.getSideToMove(),
                (int) Math.round(puzzle.getRating()), legalMoves, previousFen, previousMoveUci);
    }

    /**
     * Pista para el paso indicado — solo la casilla de origen, nunca el destino. No
     * cierra ni registra nada por sí sola; el efecto de haberla pedido (rating
     * reducido si el intento se acaba completando) se aplica al enviar el intento en
     * sí con hintUsed=true, no aquí.
     */
    @GetMapping("/{puzzleId}/hint")
    public PuzzleHintResponse hint(@PathVariable String puzzleId, @RequestParam int stepIndex, Authentication authentication) {
        requireUser(authentication);
        Puzzle puzzle = puzzleRepository.findById(puzzleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Puzzle no encontrado"));

        String[] line = puzzle.getSolutionUci().trim().split(" ");
        int lineIndex = stepIndex * 2;
        if (lineIndex < 0 || lineIndex >= line.length) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paso fuera de rango para este puzzle");
        }
        return new PuzzleHintResponse(line[lineIndex].substring(0, 2));
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

        String[] line = puzzle.getSolutionUci().trim().split(" ");
        int totalSolverMoves = (line.length + 1) / 2; // los pasos del que resuelve están en los índices pares de la línea

        if (request.stepIndex() < 0 || request.stepIndex() >= totalSolverMoves) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paso fuera de rango para este puzzle");
        }

        int lineIndex = request.stepIndex() * 2;
        String expectedMove = line[lineIndex];
        String submittedMove = request.moveUci() == null ? "" : request.moveUci().trim();
        boolean stepCorrect = submittedMove.equalsIgnoreCase(expectedMove);
        boolean isLastStep = request.stepIndex() == totalSolverMoves - 1;

        if (stepCorrect && !isLastStep) {
            // Correcto, pero quedan más pasos — no se cierra el intento todavía. La
            // respuesta del rival (siempre existe aquí, la línea alterna) se manda
            // para poder animarla, junto con las jugadas legales de la posición
            // resultante — sin esto, el cliente no podría dejarte intentar el
            // siguiente paso, no tiene motor de reglas propio (ver ADR-011).
            String opponentReplyUci = line[lineIndex + 1];
            Board resultingBoard = boardAfterLineTokens(puzzle, lineIndex + 2);
            String resultingFen = resultingBoard.toFen();
            List<String> legalMoves = resultingBoard.legalMoves().stream().map(Move::toUci).toList();
            return new PuzzleAttemptResponse(true, false, opponentReplyUci, resultingFen, legalMoves, null, null, null, null, null);
        }

        // O ha fallado, o ha acertado y era el último paso — el intento se cierra aquí.
        boolean fullyCorrect = stepCorrect; // si llegamos aquí con stepCorrect=true, isLastStep también lo era
        int stepsCorrect = stepCorrect ? request.stepIndex() + 1 : request.stepIndex();
        return finishAttempt(user, puzzle, stepsCorrect, totalSolverMoves, fullyCorrect, request.hintUsed());
    }

    /**
     * @param stepsCorrect cuántos pasos del que resuelve se acertaron en total antes de
     *                      cerrar el intento (por fallo, o por completar el último)
     * @param hintUsed si se pidió alguna pista en cualquier punto de este intento —
     *                  degrada un acierto completo a crédito parcial, igual que
     *                  acertar solo una parte de una línea de varias jugadas
     */
    private PuzzleAttemptResponse finishAttempt(User user, Puzzle puzzle, int stepsCorrect, int totalSteps,
                                                boolean fullyCorrect, boolean hintUsed) {
        UserPuzzleRating userRating = userPuzzleRatingService.findOrDefault(user);

        // El intento se trata como una partida entre el usuario y el propio puzzle —
        // reutiliza GlickoRatingService tal cual. Acierto completo sin ayuda == ganas;
        // fallo en el primer paso == pierdes; cualquier término medio (acertaste algo
        // antes de fallar, o completaste la línea pero con ayuda de una pista) ==
        // tablas, ni te quita mucho ni te suma como si hubiera sido un acierto limpio.
        Outcome userOutcome;
        if (fullyCorrect && !hintUsed) {
            userOutcome = Outcome.WIN;
        } else if (stepsCorrect == 0) {
            userOutcome = Outcome.LOSS;
        } else {
            userOutcome = Outcome.DRAW;
        }
        Outcome puzzleOutcome = switch (userOutcome) {
            case WIN -> Outcome.LOSS;
            case LOSS -> Outcome.WIN;
            case DRAW -> Outcome.DRAW;
        };

        RatingResult userBefore = new RatingResult(userRating.getRating(), userRating.getRatingDeviation(), userRating.getVolatility());
        RatingResult puzzleBefore = new RatingResult(puzzle.getRating(), puzzle.getRatingDeviation(), puzzle.getVolatility());
        RatingResult userAfter = ratingService.updateRating(userBefore, puzzleBefore, userOutcome);
        RatingResult puzzleAfter = ratingService.updateRating(puzzleBefore, userBefore, puzzleOutcome);

        userRating.applyRatingUpdate(userAfter.rating(), userAfter.ratingDeviation(), userAfter.volatility());
        puzzle.applyRatingUpdate(puzzleAfter.rating(), puzzleAfter.ratingDeviation(), puzzleAfter.volatility());
        userPuzzleRatingRepository.save(userRating);
        puzzleRepository.save(puzzle);

        // "Resuelto" para el contador de logros exige acierto completo Y sin ayuda de
        // pista — un puzzle a medias, o completado con pista, no cuenta como una
        // resolución de verdad para "Rompecabezas"/"Puzzle rush"/etc.
        boolean solvedForAchievements = fullyCorrect && !hintUsed;
        attemptRepository.save(new UserPuzzleAttempt(user, puzzle, solvedForAchievements));

        // Al final de todo, con el intento ya registrado y el rating ya actualizado —
        // igual que GameEndNotifier lo hace al terminar una partida, solo que aquí
        // hace falta llamarlo explícitamente porque resolver un puzzle no pasa por
        // GameEndNotifier en absoluto (es un sistema completamente aparte).
        achievementUnlockService.checkAndNotify(user.getId());

        // La línea de solución COMPLETA, siempre — se acierte del todo o no, es lo que
        // hay que ver ejecutarse sobre el tablero para aprender algo de verdad, no solo
        // hasta donde llegó el intento. Sin jugadas legales — el intento ya está
        // cerrado, no hace falta seguir jugando.
        List<String> solutionFenSequence = solutionFenSequence(puzzle);
        String resultingFen = solutionFenSequence.get(solutionFenSequence.size() - 1);
        List<String> solutionNotation = solutionNotation(puzzle);

        int ratingChange = (int) Math.round(userAfter.rating() - userBefore.rating());
        return new PuzzleAttemptResponse(fullyCorrect, true, null, resultingFen, null, puzzle.getSolutionUci(),
                solutionFenSequence, solutionNotation, (int) Math.round(userAfter.rating()), ratingChange);
    }

    /**
     * Una posición por cada jugada de la línea de solución, empezando por la del
     * propio puzzle (índice 0, sin ninguna jugada de la línea aplicada todavía) — para
     * poder navegarla completa con flechas anterior/siguiente una vez resuelto el
     * puzzle, ver PuzzleAttemptResponse.solutionFenSequence.
     */
    private List<String> solutionFenSequence(Puzzle puzzle) {
        List<String> fens = new java.util.ArrayList<>();
        fens.add(boardAfterLineTokens(puzzle, 0).toFen());
        String[] line = puzzle.getSolutionUci().trim().split(" ");
        for (int i = 1; i <= line.length; i++) {
            fens.add(boardAfterLineTokens(puzzle, i).toFen());
        }
        return fens;
    }

    /**
     * La notación legible ("Nf3+") de cada jugada de la línea de solución, no la UCI en
     * bruto ("g1f3") — Board.notationHistory() devuelve TODO lo acumulado desde
     * Board.initial(), así que hay que quedarse solo con el tramo final,
     * descartando las jugadas de movesUpToPosition (que no son parte de la línea en
     * sí, solo el camino hasta la posición del puzzle).
     */
    private List<String> solutionNotation(Puzzle puzzle) {
        Board board = boardAfterLineTokens(puzzle, puzzle.getSolutionUci().trim().split(" ").length);
        List<String> allNotation = board.notationHistory();
        String movesUpToPosition = puzzle.getMovesUpToPosition();
        int setupMoveCount = movesUpToPosition == null || movesUpToPosition.isBlank()
                ? 0 : movesUpToPosition.trim().split(" ").length;
        return allNotation.subList(setupMoveCount, allNotation.size());
    }

    /** Reconstruye el tablero desde el inicio (movesUpToPosition + los primeros tokenCount tokens de la línea de solución) — Board no sabe reconstruirse desde un FEN arbitrario, ver el javadoc de Puzzle.movesUpToPosition. */
    private Board boardAfterLineTokens(Puzzle puzzle, int tokenCount) {
        Board board = Board.initial();
        String movesUpToPosition = puzzle.getMovesUpToPosition();
        if (movesUpToPosition != null && !movesUpToPosition.isBlank()) {
            for (String moveUci : movesUpToPosition.trim().split(" ")) {
                board.applyMove(Move.fromUci(moveUci));
            }
        }
        String[] line = puzzle.getSolutionUci().trim().split(" ");
        for (int i = 0; i < tokenCount && i < line.length; i++) {
            board.applyMove(Move.fromUci(line[i]));
        }
        return board;
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Hace falta iniciar sesión");
        }
        return userRepository.findById(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }
}
package com.chessplatform.rating;

import com.chessplatform.engine.Move;
import com.chessplatform.matchmaking.TimeControl;
import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.entity.UserRating;
import com.chessplatform.persistence.repository.GameRepository;
import com.chessplatform.persistence.repository.UserRatingRepository;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.puzzle.PuzzleGenerationService;
import com.chessplatform.rating.GlickoRatingService.Outcome;
import com.chessplatform.rating.GlickoRatingService.RatingResult;
import com.chessplatform.realtime.GameSession;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Lo que pasa "de fondo" cuando una partida termina: actualizar el rating Glicko-2 de
 * ambos jugadores EN LA MODALIDAD QUE SE HA JUGADO y guardar la partida en el
 * historial. Separado de GameEndNotifier (que solo avisa por WebSocket y limpia el
 * registro en memoria) porque es una responsabilidad distinta — persistencia y rating,
 * no transporte.
 *
 * Si alguno de los dos jugadores no existe en la base de datos (no debería pasar en una
 * partida real, ya que ambos tuvieron que autenticarse para poder jugar), no se guarda
 * nada ni se actualiza ningún rating — se deja pasar sin más, sin tumbar el flujo de fin
 * de partida por esto.
 *
 * Partidas contra un bot (ver com.chessplatform.bot): la partida SÍ se guarda igual —
 * quien jugó contra el bot puede reproducirla después, como cualquier otra partida de su
 * historial — pero ningún rating se actualiza para ninguno de los dos lados. Son
 * partidas de práctica, no deberían poder inflar (ni desinflar) el rating de nadie, y
 * la cuenta del bot ni siquiera tiene un rating real del que partir.
 */
@Component
public class GameResultRecorder {

    /** Cuánto cambió el rating de cada jugador con esta partida en concreto, en la modalidad jugada. */
    public record RatingChanges(double whiteChange, double blackChange) {
    }

    private final UserRepository userRepository;
    private final UserRatingRepository userRatingRepository;
    private final UserRatingService userRatingService;
    private final GameRepository gameRepository;
    private final GlickoRatingService ratingService;
    private final PuzzleGenerationService puzzleGenerationService;

    public GameResultRecorder(UserRepository userRepository, UserRatingRepository userRatingRepository,
                              UserRatingService userRatingService, GameRepository gameRepository,
                              GlickoRatingService ratingService, PuzzleGenerationService puzzleGenerationService) {
        this.userRepository = userRepository;
        this.userRatingRepository = userRatingRepository;
        this.userRatingService = userRatingService;
        this.gameRepository = gameRepository;
        this.ratingService = ratingService;
        this.puzzleGenerationService = puzzleGenerationService;
    }

    /**
     * @param result "1-0" | "0-1" | "1/2-1/2"
     * @param reason "checkmate" | "resignation" | "timeout" | "stalemate" |
     *               "fifty-move-rule" | "threefold-repetition" | "agreement" | "abandonment"
     * @return los cambios de rating aplicados, o vacío si no se pudo (ver javadoc de la clase)
     */
    public Optional<RatingChanges> record(GameSession session, String result, String reason) {
        Optional<User> maybeWhite = userRepository.findById(session.whitePlayerId());
        Optional<User> maybeBlack = userRepository.findById(session.blackPlayerId());
        if (maybeWhite.isEmpty() || maybeBlack.isEmpty()) {
            return Optional.empty();
        }
        User white = maybeWhite.get();
        User black = maybeBlack.get();
        boolean isBotGame = white.isBot() || black.isBot();

        // Cualquier partida real llega aquí con un control de tiempo que coincide con
        // una de las cuatro modalidades conocidas — matchmaking, revancha y reto pasan
        // los tres por TimeControl.presetNameFor() antes de crear la GameSession. Si
        // por lo que sea no coincidiera (no debería pasar nunca en la práctica), se
        // guarda la partida igualmente pero sin tocar ningún rating, en vez de reventar
        // el fin de partida entero por esto. Lo mismo aplica, a propósito, si es una
        // partida contra un bot — ver el javadoc de la clase.
        Optional<GameMode> maybeMode = TimeControl.presetNameFor(session.initialTime(), session.increment())
                .map(GameMode::valueOf);

        double whiteChange = 0;
        double blackChange = 0;

        if (maybeMode.isPresent() && !isBotGame) {
            GameMode mode = maybeMode.get();
            UserRating whiteRating = userRatingService.findOrDefault(white, mode);
            UserRating blackRating = userRatingService.findOrDefault(black, mode);

            RatingResult whiteBefore = new RatingResult(whiteRating.getRating(), whiteRating.getRatingDeviation(), whiteRating.getVolatility());
            RatingResult blackBefore = new RatingResult(blackRating.getRating(), blackRating.getRatingDeviation(), blackRating.getVolatility());

            RatingResult whiteAfter = ratingService.updateRating(whiteBefore, blackBefore, outcomeForWhite(result));
            RatingResult blackAfter = ratingService.updateRating(blackBefore, whiteBefore, outcomeForBlack(result));

            whiteRating.applyRatingUpdate(whiteAfter.rating(), whiteAfter.ratingDeviation(), whiteAfter.volatility());
            blackRating.applyRatingUpdate(blackAfter.rating(), blackAfter.ratingDeviation(), blackAfter.volatility());
            userRatingRepository.save(whiteRating);
            userRatingRepository.save(blackRating);

            whiteChange = whiteAfter.rating() - whiteBefore.rating();
            blackChange = blackAfter.rating() - blackBefore.rating();
        }

        String timeControlLabel = "%d+%d".formatted(session.initialTime().toMinutes(), session.increment().getSeconds());
        Game game = new Game(white, black, timeControlLabel);
        game.setResult(result);
        game.setReason(reason);
        game.setMoveList(session.board().moveHistory().stream()
                .map(Move::toUci)
                .collect(Collectors.joining(" ")));
        game.setRatingChanges(whiteChange, blackChange);
        gameRepository.save(game);

        // Al final de todo, con la partida ya guardada y su id ya asignado (UUID
        // generado en memoria, no hay que esperar a nada más) — @Async hace que esta
        // llamada vuelva al instante, el análisis de verdad corre en segundo plano, ver
        // el javadoc de PuzzleGenerationService.
        puzzleGenerationService.generateFromGame(game);

        return Optional.of(new RatingChanges(whiteChange, blackChange));
    }

    private Outcome outcomeForWhite(String result) {
        return switch (result) {
            case "1-0" -> Outcome.WIN;
            case "0-1" -> Outcome.LOSS;
            case "1/2-1/2" -> Outcome.DRAW;
            default -> throw new IllegalArgumentException("Resultado de partida desconocido: " + result);
        };
    }

    private Outcome outcomeForBlack(String result) {
        return switch (result) {
            case "1-0" -> Outcome.LOSS;
            case "0-1" -> Outcome.WIN;
            case "1/2-1/2" -> Outcome.DRAW;
            default -> throw new IllegalArgumentException("Resultado de partida desconocido: " + result);
        };
    }
}
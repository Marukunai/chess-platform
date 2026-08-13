package com.chessplatform.rating;

import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.GameRepository;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.rating.GlickoRatingService.Outcome;
import com.chessplatform.rating.GlickoRatingService.RatingResult;
import com.chessplatform.realtime.GameSession;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Lo que pasa "de fondo" cuando una partida termina: actualizar el rating Glicko-2 de
 * ambos jugadores y guardar la partida en el historial. Separado de GameEndNotifier
 * (que solo avisa por WebSocket y limpia el registro en memoria) porque es una
 * responsabilidad distinta — persistencia y rating, no transporte.
 *
 * Si alguno de los dos jugadores no existe en la base de datos (no debería pasar en una
 * partida real, ya que ambos tuvieron que autenticarse para poder jugar), no se guarda
 * nada ni se actualiza ningún rating — se deja pasar sin más, sin tumbar el flujo de fin
 * de partida por esto.
 */
@Component
public class GameResultRecorder {

    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final GlickoRatingService ratingService;

    public GameResultRecorder(UserRepository userRepository, GameRepository gameRepository,
                              GlickoRatingService ratingService) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
        this.ratingService = ratingService;
    }

    /**
     * @param result "1-0" | "0-1" | "1/2-1/2"
     */
    public void record(GameSession session, String result) {
        Optional<User> maybeWhite = userRepository.findById(session.whitePlayerId());
        Optional<User> maybeBlack = userRepository.findById(session.blackPlayerId());
        if (maybeWhite.isEmpty() || maybeBlack.isEmpty()) {
            return;
        }
        User white = maybeWhite.get();
        User black = maybeBlack.get();

        RatingResult whiteBefore = new RatingResult(white.getRating(), white.getRatingDeviation(), white.getVolatility());
        RatingResult blackBefore = new RatingResult(black.getRating(), black.getRatingDeviation(), black.getVolatility());

        RatingResult whiteAfter = ratingService.updateRating(whiteBefore, blackBefore, outcomeForWhite(result));
        RatingResult blackAfter = ratingService.updateRating(blackBefore, whiteBefore, outcomeForBlack(result));

        white.applyRatingUpdate(whiteAfter.rating(), whiteAfter.ratingDeviation(), whiteAfter.volatility());
        black.applyRatingUpdate(blackAfter.rating(), blackAfter.ratingDeviation(), blackAfter.volatility());
        userRepository.save(white);
        userRepository.save(black);

        String timeControlLabel = "%d+%d".formatted(session.initialTime().toMinutes(), session.increment().getSeconds());
        Game game = new Game(white, black, timeControlLabel);
        game.setResult(result);
        gameRepository.save(game);
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
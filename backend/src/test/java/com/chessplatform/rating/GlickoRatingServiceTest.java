package com.chessplatform.rating;

import com.chessplatform.rating.GlickoRatingService.Outcome;
import com.chessplatform.rating.GlickoRatingService.RatingResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GlickoRatingServiceTest {

    private final GlickoRatingService glicko = new GlickoRatingService();

    // Valores intermedios tomados directamente del ejemplo numérico del paper original
    // de Glickman (http://www.glicko.net/glicko/glicko2.pdf, sección "Example
    // calculation"), para verificar que g() y expectedScore() calculan exactamente lo
    // mismo que el paper antes de fiarse del resultado final tras las 8 fases completas.
    @Test
    void gAndExpectedScoreMatchThePublishedGlickoPaperExample() {
        // Jugador de referencia: rating 1500 (mu=0). Rival del ejemplo: rating 1400, RD 30.
        double opponentPhi = 30 / 173.7178;
        double g = glicko.g(opponentPhi);
        assertThat(g).isCloseTo(0.9955, within(0.001));

        double mu = 0;
        double opponentMu = (1400 - 1500) / 173.7178;
        double e = glicko.expectedScore(mu, opponentMu, g);
        assertThat(e).isCloseTo(0.639, within(0.001));
    }

    @Test
    void ratingStaysExactlyTheSameAfterADrawBetweenEquallyRatedPlayers() {
        // Con el mismo rating, el resultado esperado ya es 0.5 — un empate es
        // exactamente lo esperado, así que Δ=0 y el rating no se mueve.
        RatingResult player = new RatingResult(1500, 200, 0.06);
        RatingResult opponent = new RatingResult(1500, 200, 0.06);

        RatingResult result = glicko.updateRating(player, opponent, Outcome.DRAW);

        assertThat(result.rating()).isCloseTo(1500, within(0.001));
    }

    @Test
    void winningIncreasesRatingAndLosingDecreasesIt() {
        RatingResult player = new RatingResult(1500, 200, 0.06);
        RatingResult opponent = new RatingResult(1500, 200, 0.06);

        RatingResult afterWin = glicko.updateRating(player, opponent, Outcome.WIN);
        RatingResult afterLoss = glicko.updateRating(player, opponent, Outcome.LOSS);

        assertThat(afterWin.rating()).isGreaterThan(player.rating());
        assertThat(afterLoss.rating()).isLessThan(player.rating());
    }

    @Test
    void beatingAMuchStrongerOpponentGainsMoreRatingThanBeatingAMuchWeakerOne() {
        RatingResult player = new RatingResult(1500, 100, 0.06);
        RatingResult strongerOpponent = new RatingResult(1900, 100, 0.06);
        RatingResult weakerOpponent = new RatingResult(1100, 100, 0.06);

        RatingResult afterBeatingStronger = glicko.updateRating(player, strongerOpponent, Outcome.WIN);
        RatingResult afterBeatingWeaker = glicko.updateRating(player, weakerOpponent, Outcome.WIN);

        double gainVsStronger = afterBeatingStronger.rating() - player.rating();
        double gainVsWeaker = afterBeatingWeaker.rating() - player.rating();

        assertThat(gainVsStronger).isGreaterThan(gainVsWeaker);
    }

    @Test
    void ratingDeviationDecreasesAfterPlayingAGameInNormalConditions() {
        RatingResult player = new RatingResult(1500, 200, 0.06);
        RatingResult opponent = new RatingResult(1500, 200, 0.06);

        RatingResult result = glicko.updateRating(player, opponent, Outcome.WIN);

        assertThat(result.ratingDeviation()).isLessThan(player.ratingDeviation());
    }

    @Test
    void newPlayerDefaultsMatchGlickosRecommendedStartingValues() {
        assertThat(GlickoRatingService.DEFAULT_RATING).isEqualTo(1500.0);
        assertThat(GlickoRatingService.DEFAULT_RATING_DEVIATION).isEqualTo(350.0);
        assertThat(GlickoRatingService.DEFAULT_VOLATILITY).isEqualTo(0.06);
    }
}
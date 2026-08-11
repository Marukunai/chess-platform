package com.chessplatform.rating;

import org.springframework.stereotype.Service;

/**
 * Sistema de rating Glicko-2 (Glickman, 2001). A diferencia de Elo, cada jugador tiene:
 *  - rating (r): la fuerza estimada, igual que en Elo.
 *  - rating deviation (RD): cuánta incertidumbre hay sobre ese rating (baja tras jugar mucho).
 *  - volatility (σ): cuánto varía el rendimiento del jugador entre partidas.
 *
 * TODO (Fase 1): implementar el algoritmo completo según el paper de Glickman:
 * http://www.glicko.net/glicko/glicko2.pdf
 */
@Service
public class GlickoRatingService {

    public static final double DEFAULT_RATING = 1500.0;
    public static final double DEFAULT_RATING_DEVIATION = 350.0;
    public static final double DEFAULT_VOLATILITY = 0.06;

    public record RatingResult(double rating, double ratingDeviation, double volatility) {
    }

    public enum Outcome {
        WIN(1.0), LOSS(0.0), DRAW(0.5);

        public final double score;

        Outcome(double score) {
            this.score = score;
        }
    }

    /**
     * Calcula el nuevo rating de un jugador tras una partida contra un oponente.
     */
    public RatingResult updateRating(RatingResult player, RatingResult opponent, Outcome outcome) {
        // TODO (Fase 1): implementar los pasos del algoritmo Glicko-2
        throw new UnsupportedOperationException("Pendiente de implementar");
    }
}

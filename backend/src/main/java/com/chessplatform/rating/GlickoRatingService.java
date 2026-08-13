package com.chessplatform.rating;

import org.springframework.stereotype.Service;

/**
 * Sistema de rating Glicko-2 (Glickman, 2001): http://www.glicko.net/glicko/glicko2.pdf
 *
 * A diferencia de Elo, cada jugador tiene:
 *  - rating (r): la fuerza estimada, igual que en Elo.
 *  - rating deviation (RD): cuánta incertidumbre hay sobre ese rating (baja tras jugar
 *    mucho, sube cuanto más tiempo lleva un jugador sin competir).
 *  - volatility (σ): cuánto varía el rendimiento del jugador entre partidas.
 *
 * Esta implementación actualiza tras UNA sola partida, no un "periodo de rating" con
 * varias (que es como lo plantea el paper original) — así que las fórmulas generales
 * (que suman sobre todos los rivales del periodo) se simplifican al caso de un único
 * término. Es la simplificación práctica habitual para juegos 1v1 por turnos como el
 * ajedrez online, donde cada partida se resuelve al momento.
 */
@Service
public class GlickoRatingService {

    public static final double DEFAULT_RATING = 1500.0;
    public static final double DEFAULT_RATING_DEVIATION = 350.0;
    public static final double DEFAULT_VOLATILITY = 0.06;

    private static final double GLICKO2_SCALE = 173.7178;
    // Constante del sistema (τ): controla cuánto puede cambiar la volatilidad de una
    // partida a otra. Glickman recomienda entre 0.3 y 1.2; 0.5 es un valor intermedio
    // razonable sin datos históricos propios que lo justifiquen mejor.
    private static final double SYSTEM_CONSTANT_TAU = 0.5;
    private static final double CONVERGENCE_TOLERANCE = 0.000001;

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
        // Paso 1: pasar a la escala Glicko-2 (μ, φ) — la escala Elo-like (~1500) no es
        // la que usan las fórmulas del paper.
        double mu = toGlicko2Scale(player.rating());
        double phi = toGlicko2RatingDeviation(player.ratingDeviation());
        double opponentMu = toGlicko2Scale(opponent.rating());
        double opponentPhi = toGlicko2RatingDeviation(opponent.ratingDeviation());
        double sigma = player.volatility();

        // Paso 2: g(φ_rival) y valor esperado E
        double g = g(opponentPhi);
        double e = expectedScore(mu, opponentMu, g);

        // Paso 3: varianza estimada v
        double v = 1.0 / (g * g * e * (1 - e));

        // Paso 4: mejora estimada Δ
        double delta = v * g * (outcome.score - e);

        // Paso 5: nueva volatilidad σ' — no tiene solución cerrada, se resuelve por
        // aproximación numérica (algoritmo de Illinois, ver Apéndice B del paper).
        double newSigma = computeNewVolatility(phi, v, delta, sigma);

        // Paso 6: φ* — el RD "pre-partida" ya con la nueva volatilidad incorporada
        double phiStar = Math.sqrt(phi * phi + newSigma * newSigma);

        // Paso 7: nuevos φ' y μ'
        double newPhi = 1.0 / Math.sqrt(1.0 / (phiStar * phiStar) + 1.0 / v);
        double newMu = mu + newPhi * newPhi * g * (outcome.score - e);

        // Paso 8: de vuelta a la escala original (~1500)
        double newRating = fromGlicko2Scale(newMu);
        double newRatingDeviation = fromGlicko2RatingDeviation(newPhi);

        return new RatingResult(newRating, newRatingDeviation, newSigma);
    }

    // Paquete-visible (no private) a propósito: GlickoRatingServiceTest los comprueba
    // directamente contra los valores intermedios publicados en el ejemplo numérico del
    // paper de Glickman, para verificar que el cálculo central es correcto antes de
    // fiarse del resultado final tras las 8 fases completas.

    double g(double phi) {
        return 1.0 / Math.sqrt(1.0 + 3.0 * phi * phi / (Math.PI * Math.PI));
    }

    double expectedScore(double mu, double opponentMu, double g) {
        return 1.0 / (1.0 + Math.exp(-g * (mu - opponentMu)));
    }

    /**
     * Algoritmo de Illinois (una variante de regula falsi) para resolver σ' — el paper no
     * da una fórmula cerrada para esto, hay que aproximarla iterativamente. Ver el
     * Apéndice B del paper de Glickman para la derivación completa.
     */
    private double computeNewVolatility(double phi, double v, double delta, double sigma) {
        double a = Math.log(sigma * sigma);
        double deltaSquared = delta * delta;
        double phiSquared = phi * phi;

        double lowerBoundA = a;
        double upperBoundB;
        if (deltaSquared > phiSquared + v) {
            upperBoundB = Math.log(deltaSquared - phiSquared - v);
        } else {
            int k = 1;
            while (f(a - k * SYSTEM_CONSTANT_TAU, deltaSquared, phiSquared, v, a) < 0) {
                k++;
            }
            upperBoundB = a - k * SYSTEM_CONSTANT_TAU;
        }

        double fA = f(lowerBoundA, deltaSquared, phiSquared, v, a);
        double fB = f(upperBoundB, deltaSquared, phiSquared, v, a);

        while (Math.abs(upperBoundB - lowerBoundA) > CONVERGENCE_TOLERANCE) {
            double candidateC = lowerBoundA + (lowerBoundA - upperBoundB) * fA / (fB - fA);
            double fC = f(candidateC, deltaSquared, phiSquared, v, a);

            if (fC * fB < 0) {
                lowerBoundA = upperBoundB;
                fA = fB;
            } else {
                fA = fA / 2.0;
            }
            upperBoundB = candidateC;
            fB = fC;
        }

        return Math.exp(lowerBoundA / 2.0);
    }

    private double f(double x, double deltaSquared, double phiSquared, double v, double a) {
        double expX = Math.exp(x);
        double numerator = expX * (deltaSquared - phiSquared - v - expX);
        double denominator = 2.0 * Math.pow(phiSquared + v + expX, 2);
        return (numerator / denominator) - ((x - a) / (SYSTEM_CONSTANT_TAU * SYSTEM_CONSTANT_TAU));
    }

    private double toGlicko2Scale(double rating) {
        return (rating - DEFAULT_RATING) / GLICKO2_SCALE;
    }

    private double fromGlicko2Scale(double mu) {
        return GLICKO2_SCALE * mu + DEFAULT_RATING;
    }

    private double toGlicko2RatingDeviation(double ratingDeviation) {
        return ratingDeviation / GLICKO2_SCALE;
    }

    private double fromGlicko2RatingDeviation(double phi) {
        return GLICKO2_SCALE * phi;
    }
}
package com.chessplatform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Una posición táctica sacada de una partida real ya jugada — generada sola, en
 * segundo plano, justo después de que esa partida terminara (ver
 * puzzle.PuzzleGenerationService). Un único movimiento como solución, no una línea
 * forzada completa — detectar que la CONTINUACIÓN completa siga siendo forzada
 * necesitaría analizar varias jugadas más por delante, y para una primera versión "aquí
 * hay una jugada claramente mejor que las demás" ya es un puzzle de verdad, aunque no
 * sea de varios movimientos.
 *
 * sourceGameId no es una relación @ManyToOne a propósito: si la partida de origen se
 * borrara algún día (no hay ningún camino para eso todavía, pero por si acaso), el
 * puzzle en sí sigue siendo válido y resoluble sin ella — solo se pierde la posibilidad
 * de enlazar de vuelta a "de qué partida salió este puzzle", que es un extra, no algo
 * de lo que dependa el puzzle para funcionar.
 */
@Entity
@Table(name = "puzzles")
public class Puzzle {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "source_game_id", nullable = false)
    private String sourceGameId;

    @Column(nullable = false)
    private String fen;

    // "white" | "black" — de quién es el turno en la posición del puzzle (quien tiene
    // que encontrar la jugada correcta, no quien acaba de cometer el error).
    @Column(name = "side_to_move", nullable = false)
    private String sideToMove;

    @Column(name = "solution_uci", nullable = false)
    private String solutionUci;

    // Las jugadas legales de la posición, en UCI separadas por espacio — el cliente web
    // no tiene ningún motor de reglas propio (ver ADR-011), así que el puzzle tiene que
    // llevar esta información consigo. Se calcula UNA vez, al generar el puzzle (ver
    // PuzzleGenerationService, que sí tiene un Board real disponible en ese momento),
    // no en cada petición.
    @Column(name = "legal_moves_uci", nullable = false)
    private String legalMovesUci;

    // Rating Glicko-2 propio del puzzle — empieza en los valores por defecto de
    // siempre y se ajusta con el rendimiento de quien lo resuelve, exactamente igual
    // que el rating de un jugador (ver puzzle.PuzzleRatingService, que reutiliza
    // GlickoRatingService tal cual).
    @Column(nullable = false)
    private double rating = 1500.0;

    @Column(name = "rating_deviation", nullable = false)
    private double ratingDeviation = 350.0;

    @Column(nullable = false)
    private double volatility = 0.06;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Puzzle() {
        // JPA
    }

    public Puzzle(String sourceGameId, String fen, String sideToMove, String solutionUci, String legalMovesUci) {
        this.sourceGameId = sourceGameId;
        this.fen = fen;
        this.sideToMove = sideToMove;
        this.solutionUci = solutionUci;
        this.legalMovesUci = legalMovesUci;
    }

    public String getId() {
        return id;
    }

    public String getSourceGameId() {
        return sourceGameId;
    }

    public String getFen() {
        return fen;
    }

    public String getSideToMove() {
        return sideToMove;
    }

    public String getSolutionUci() {
        return solutionUci;
    }

    public String getLegalMovesUci() {
        return legalMovesUci;
    }

    public double getRating() {
        return rating;
    }

    public double getRatingDeviation() {
        return ratingDeviation;
    }

    public double getVolatility() {
        return volatility;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Mismo motivo de siempre para un método de dominio en vez de setters sueltos — los tres valores son el resultado de un mismo cálculo Glicko-2, nunca se actualizan por separado. */
    public void applyRatingUpdate(double rating, double ratingDeviation, double volatility) {
        this.rating = rating;
        this.ratingDeviation = ratingDeviation;
        this.volatility = volatility;
    }
}
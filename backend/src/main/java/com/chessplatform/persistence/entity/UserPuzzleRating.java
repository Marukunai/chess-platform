package com.chessplatform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * El rating Glicko-2 de un usuario resolviendo puzzles — un sistema completamente
 * aparte del rating de partidas (UserRating), aunque reutiliza exactamente el mismo
 * cálculo (GlickoRatingService). A diferencia de UserRating, esto NO es por modalidad
 * — una única fila por usuario, la habilidad resolviendo tácticas no depende de si
 * juegas bullet o clásicas.
 *
 * Creación perezosa, mismo criterio que UserRating: no existe hasta que el usuario
 * resuelve su primer puzzle de verdad — ver puzzle.UserPuzzleRatingService.
 */
@Entity
@Table(name = "user_puzzle_ratings", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
public class UserPuzzleRating {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private double rating = 1500.0;

    @Column(name = "rating_deviation", nullable = false)
    private double ratingDeviation = 350.0;

    @Column(nullable = false)
    private double volatility = 0.06;

    protected UserPuzzleRating() {
        // JPA
    }

    public UserPuzzleRating(User user) {
        this.user = user;
    }

    public String getId() {
        return id;
    }

    public User getUser() {
        return user;
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

    public void applyRatingUpdate(double rating, double ratingDeviation, double volatility) {
        this.rating = rating;
        this.ratingDeviation = ratingDeviation;
        this.volatility = volatility;
    }
}
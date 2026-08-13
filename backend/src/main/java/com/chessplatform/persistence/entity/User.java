package com.chessplatform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private double rating = 1500.0;

    @Column(nullable = false)
    private double ratingDeviation = 350.0;

    @Column(nullable = false)
    private double volatility = 0.06;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {
        // JPA
    }

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
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

    /**
     * Aplica el resultado de GlickoRatingService tras una partida. Método de dominio en
     * vez de setters sueltos — deja claro que los tres valores se actualizan siempre
     * juntos (son el resultado de un mismo cálculo Glicko-2), nunca por separado.
     */
    public void applyRatingUpdate(double rating, double ratingDeviation, double volatility) {
        this.rating = rating;
        this.ratingDeviation = ratingDeviation;
        this.volatility = volatility;
    }
}
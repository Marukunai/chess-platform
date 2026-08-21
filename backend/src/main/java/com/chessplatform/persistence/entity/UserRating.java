package com.chessplatform.persistence.entity;

import com.chessplatform.rating.GameMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * El rating Glicko-2 de un usuario en UNA modalidad concreta — bullet, blitz, rápidas y
 * clásicas son ratings completamente independientes entre sí (jugar mucho bullet no
 * debería inflar tu rating de clásicas, ni al revés), así que en vez de un único rating
 * en User, cada combinación (usuario, modalidad) tiene su propia fila aquí.
 *
 * Creación perezosa a propósito: un usuario recién registrado no tiene NINGUNA fila
 * todavía — la fila de verdad solo se persiste cuando GameResultRecorder graba el
 * resultado de una partida jugada en esa modalidad. Buscar partida (MatchmakingService)
 * o consultar un perfil (UserController) pueden necesitar "el rating en esta modalidad"
 * sin que eso cree ninguna fila — ver UserRatingService.findOrDefault(), que en ese caso
 * devuelve una instancia con los valores por defecto de Glicko-2 sin guardarla. Nunca se
 * crean las cuatro de golpe al registrarse: alguien que nunca ha jugado bullet no tiene
 * por qué aparecer en absoluto en el ranking de bullet.
 */
@Entity
@Table(name = "user_ratings", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "mode"}))
public class UserRating {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GameMode mode;

    @Column(nullable = false)
    private double rating = 1500.0;

    @Column(nullable = false)
    private double ratingDeviation = 350.0;

    @Column(nullable = false)
    private double volatility = 0.06;

    protected UserRating() {
        // JPA
    }

    public UserRating(User user, GameMode mode) {
        this.user = user;
        this.mode = mode;
    }

    public String getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public GameMode getMode() {
        return mode;
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

    /** Mismo motivo que User.applyRatingUpdate() tenía antes de este cambio: los tres valores son el resultado de un mismo cálculo Glicko-2, siempre juntos. */
    public void applyRatingUpdate(double rating, double ratingDeviation, double volatility) {
        this.rating = rating;
        this.ratingDeviation = ratingDeviation;
        this.volatility = volatility;
    }
}
package com.chessplatform.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "games")
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    private User whitePlayer;

    @ManyToOne(optional = false)
    private User blackPlayer;

    @Column(nullable = false)
    private String result; // "1-0" | "0-1" | "1/2-1/2"

    // Jugadas en notación UCI separadas por espacios ("e2e4 e7e5 g1f3 ..."), no PGN real
    // (eso — notación algebraica con desambiguación, símbolos de jaque/mate — sigue
    // siendo su propio ítem de Fase 2: importación/exportación de PGN). El servidor
    // reconstruye la secuencia de posiciones a partir de esto — ver GameReplayService.
    @Column(columnDefinition = "TEXT")
    private String moveList;

    @Column(nullable = false)
    private String timeControl; // ej. "5+3"

    // Cuánto cambió el rating Glicko-2 de cada jugador CON ESTA partida en concreto —
    // se guarda en el momento (no se recalcula después), porque el rating de cada
    // jugador sigue cambiando con partidas posteriores y ya no se podría reconstruir
    // cuál fue el cambio de esta en particular a partir del rating actual. Nullable:
    // partidas de antes de que existiera este campo, o si el guardado del resultado
    // falló a medias, no tienen este dato.
    @Column
    private Double whiteRatingChange;

    @Column
    private Double blackRatingChange;

    @Column(nullable = false, updatable = false)
    private Instant playedAt = Instant.now();

    protected Game() {
        // JPA
    }

    public Game(User whitePlayer, User blackPlayer, String timeControl) {
        this.whitePlayer = whitePlayer;
        this.blackPlayer = blackPlayer;
        this.timeControl = timeControl;
    }

    public String getId() {
        return id;
    }

    public User getWhitePlayer() {
        return whitePlayer;
    }

    public User getBlackPlayer() {
        return blackPlayer;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getMoveList() {
        return moveList;
    }

    public void setMoveList(String moveList) {
        this.moveList = moveList;
    }

    public String getTimeControl() {
        return timeControl;
    }

    public Instant getPlayedAt() {
        return playedAt;
    }

    public Double getWhiteRatingChange() {
        return whiteRatingChange;
    }

    public Double getBlackRatingChange() {
        return blackRatingChange;
    }

    /** Los dos cambios de rating se fijan siempre juntos — son el resultado de la misma partida. */
    public void setRatingChanges(double whiteRatingChange, double blackRatingChange) {
        this.whiteRatingChange = whiteRatingChange;
        this.blackRatingChange = blackRatingChange;
    }
}
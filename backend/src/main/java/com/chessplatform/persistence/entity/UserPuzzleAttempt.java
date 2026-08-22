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

import java.time.Instant;

/**
 * Que un usuario ya intentó un puzzle concreto, y si acertó — un único intento por
 * (usuario, puzzle), nunca se vuelve a ofrecer el mismo puzzle dos veces a la misma
 * persona (ver PuzzleController.next(), que excluye los ya intentados al elegir el
 * siguiente).
 */
@Entity
@Table(name = "user_puzzle_attempts", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "puzzle_id"}))
public class UserPuzzleAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "puzzle_id")
    private Puzzle puzzle;

    @Column(nullable = false)
    private boolean solved;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt = Instant.now();

    protected UserPuzzleAttempt() {
        // JPA
    }

    public UserPuzzleAttempt(User user, Puzzle puzzle, boolean solved) {
        this.user = user;
        this.puzzle = puzzle;
        this.solved = solved;
    }

    public String getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public Puzzle getPuzzle() {
        return puzzle;
    }

    public boolean isSolved() {
        return solved;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
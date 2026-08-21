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
 * La fecha exacta en que un usuario desbloqueó un logro concreto — a diferencia del
 * resto del sistema de logros (que se calcula al vuelo sin guardar nada, ver
 * AchievementService), ESTO sí se persiste, porque "cuándo" y "quién fue el primero" no
 * se pueden reconstruir después del hecho: si no se guarda en el momento exacto en que
 * pasa, esa información se pierde para siempre. achievementId es el id del catálogo
 * (p. ej. "primera-partida"), no una relación a ninguna tabla — el catálogo sigue
 * viviendo en código, ver AchievementCatalog.
 */
@Entity
@Table(name = "user_achievement_unlocks", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "achievement_id"}))
public class UserAchievementUnlock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "achievement_id", nullable = false)
    private String achievementId;

    @Column(nullable = false, updatable = false)
    private Instant unlockedAt = Instant.now();

    protected UserAchievementUnlock() {
        // JPA
    }

    public UserAchievementUnlock(User user, String achievementId) {
        this.user = user;
        this.achievementId = achievementId;
    }

    public String getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getAchievementId() {
        return achievementId;
    }

    public Instant getUnlockedAt() {
        return unlockedAt;
    }
}
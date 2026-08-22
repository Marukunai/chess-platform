package com.chessplatform.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {

    // El rating Glicko-2 NO vive aquí a propósito — desde que el ranking se separó por
    // modalidad (bullet/blitz/rápidas/clásicas), cada combinación (usuario, modalidad)
    // tiene su propia fila en UserRating, con creación perezosa la primera vez que hace
    // falta. Ver UserRatingService y el ADR correspondiente.
    // + la contraseña actual = 5 en total al comprobar reutilización, ver
    // matchesAnyRecentPassword() — "las últimas 5 usadas" cuenta la que tienes puesta
    // ahora mismo como una de esas cinco, no aparte.
    private static final int PASSWORD_HISTORY_SIZE = 4;

    @Getter
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Getter
    @Column(nullable = false, unique = true)
    private String username;

    @Getter
    @Column(nullable = false)
    private String passwordHash;

    // Los hashes de las contraseñas anteriores (la más reciente primero), para poder
    // rechazar que alguien "cambie" su contraseña a una que ya tuvo hace poco — ver
    // changePassword() y matchesAnyRecentPassword(). Solo hashes, nunca contraseñas en
    // claro, igual que passwordHash.
    @ElementCollection
    @CollectionTable(name = "user_password_history", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "password_hash")
    @OrderColumn(name = "position")
    private List<String> passwordHistory = new ArrayList<>();

    // Opcionales los dos — un jugador puede no querer decir de dónde es, o no tener
    // ganas de poner una imagen. avatarUrl es solo un enlace a una imagen ya alojada en
    // otro sitio (imgur, gravatar, lo que sea) — no subimos ni guardamos ningún archivo
    // nosotros, evita meter almacenamiento de ficheros para esto.
    @Getter
    @Column
    private String country;

    @Getter
    @Column
    private String avatarUrl;

    /**
     * -- GETTER --
     * Para los logros de antigüedad de cuenta (AchievementService) — nadie más lo necesitaba hasta ahora.
     */
    @Getter
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // false para cualquier cuenta creada por AuthController.register() de verdad — solo
    // las tres filas que crea BotAccountSeeder al arrancar (una por BotDifficulty) lo
    // llevan a true. Reutiliza TODA la infraestructura ya existente para "un jugador"
    // (avatar, nombre, aparecer en Game.whitePlayer/blackPlayer sin tocar esa entidad
    // en absoluto) en vez de inventar un concepto de "oponente" aparte solo para bots —
    // ver ADR correspondiente. Sin contraseña utilizable (igual que una cuenta borrada,
    // ver anonymizeForDeletion): nadie puede iniciar sesión como un bot.
    @Column(nullable = false)
    private boolean bot = false;

    // null = cuenta activa. Con valor = borrada (ver anonymizeForDeletion) — no se
    // elimina la fila de verdad porque las partidas de OTROS jugadores tienen una
    // relación @ManyToOne hacia este User; borrar la fila rompería su historial o (con
    // cascade) se lo llevaría por delante, algo que ellos no pidieron. En su lugar el
    // usuario se anonimiza y sigue existiendo, solo que ya no se puede entrar con él ni
    // aparece en el ranking.
    @Column
    private Instant deletedAt;

    protected User() {
        // JPA
    }

    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isBot() {
        return bot;
    }

    /** Solo lo llama BotAccountSeeder, una vez por cada BotDifficulty al arrancar la aplicación si la cuenta todavía no existe. */
    public void markAsBot() {
        this.bot = true;
    }

    /**
     * Los tres campos editables por el propio usuario, siempre juntos — la validación
     * de verdad (username no vacío, no duplicado) vive en el controlador, que es quien
     * tiene acceso al repositorio para comprobar duplicados; aquí solo se asignan.
     */
    public void updateProfile(String username, String country, String avatarUrl) {
        this.username = username;
        this.country = country;
        this.avatarUrl = avatarUrl;
    }

    /**
     * ¿rawPassword (en claro, sin hashear) coincide con la contraseña actual o con
     * alguna de las últimas PASSWORD_HISTORY_SIZE anteriores? El PasswordEncoder hay
     * que pasarlo desde fuera — un hash de BCrypt no se puede comparar con == ni con
     * equals(), solo con encoder.matches(), y User no tiene (ni debería tener) su
     * propio PasswordEncoder inyectado.
     */
    public boolean matchesAnyRecentPassword(String rawPassword, PasswordEncoder encoder) {
        if (encoder.matches(rawPassword, passwordHash)) {
            return true;
        }
        return passwordHistory.stream().anyMatch(oldHash -> encoder.matches(rawPassword, oldHash));
    }

    /**
     * Antes de reemplazar el hash, empuja el actual al historial (el más reciente
     * primero, con addFirst — SequencedCollection, Java 21) y recorta a
     * PASSWORD_HISTORY_SIZE con removeLast si hiciera falta.
     */
    public void changePassword(String newPasswordHash) {
        passwordHistory.addFirst(this.passwordHash);
        while (passwordHistory.size() > PASSWORD_HISTORY_SIZE) {
            passwordHistory.removeLast();
        }
        this.passwordHash = newPasswordHash;
    }

    /**
     * Borrado lógico — ver el javadoc de deletedAt. anonymizedUsername lo decide el
     * controlador (necesita comprobar que no choque con nadie más, y aquí no hay
     * acceso al repositorio para eso). unusablePasswordHash: cualquier valor que BCrypt
     * nunca vaya a producir al hashear una contraseña real de verdad — así el login
     * queda descartado sin necesidad de un campo "activo" aparte que comprobar en cada
     * sitio.
     */
    public void anonymizeForDeletion(String anonymizedUsername, String unusablePasswordHash, Instant deletedAt) {
        this.username = anonymizedUsername;
        this.passwordHash = unusablePasswordHash;
        this.country = null;
        this.avatarUrl = null;
        this.deletedAt = deletedAt;
    }
}
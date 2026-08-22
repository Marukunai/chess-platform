package com.chessplatform.bot;

import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Crea las tres cuentas de bot (una por BotDifficulty) la primera vez que arranca la
 * aplicación, si todavía no existen — las siguientes veces las encuentra ya creadas y
 * no hace nada. @EventListener(ApplicationReadyEvent) en vez de @PostConstruct: se
 * ejecuta con el contexto de Spring ya completamente listo (incluida la conexión a
 * base de datos), evitando problemas de orden de inicialización.
 *
 * Reutilizan toda la infraestructura de User tal cual — nombre, avatar (sin poner,
 * queda con el icono por defecto del cliente), y sobre todo: aparecer en
 * Game.whitePlayer/blackPlayer sin que esa entidad necesite saber que uno de los dos
 * lados es un bot. Password aleatoria e inutilizable, igual que una cuenta borrada (ver
 * User.anonymizeForDeletion) — nadie puede iniciar sesión como un bot.
 */
@Component
public class BotAccountSeeder {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BotAccountSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedBotAccounts() {
        for (BotDifficulty difficulty : BotDifficulty.values()) {
            String username = usernameFor(difficulty);
            if (userRepository.findByUsername(username).isEmpty()) {
                User bot = new User(username, passwordEncoder.encode(UUID.randomUUID().toString()));
                bot.markAsBot();
                userRepository.save(bot);
            }
        }
    }

    /** Único sitio donde vive la correspondencia modalidad → nombre visible — cualquier otro código que necesite encontrar la cuenta de un bot concreto pasa por aquí, no reconstruye el nombre a mano. */
    public static String usernameFor(BotDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> "Stockfish (Fácil)";
            case MEDIUM -> "Stockfish (Media)";
            case HARD -> "Stockfish (Difícil)";
        };
    }

    /** El camino inverso de usernameFor() — para los logros de bots (AchievementService), que necesitan saber a qué dificultad se jugó a partir del nombre del rival guardado en Game.whitePlayer/blackPlayer. Vacío si el nombre no coincide con ninguna cuenta de bot conocida (un rival humano cualquiera, por ejemplo). */
    public static Optional<BotDifficulty> difficultyFor(String username) {
        for (BotDifficulty difficulty : BotDifficulty.values()) {
            if (usernameFor(difficulty).equals(username)) {
                return Optional.of(difficulty);
            }
        }
        return Optional.empty();
    }
}
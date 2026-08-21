package com.chessplatform.persistence.controller;

import com.chessplatform.persistence.dto.ChangePasswordRequest;
import com.chessplatform.persistence.dto.DeleteAccountRequest;
import com.chessplatform.persistence.dto.LeaderboardEntryResponse;
import com.chessplatform.persistence.dto.UpdateProfileRequest;
import com.chessplatform.persistence.dto.UserProfileResponse;
import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.entity.UserRating;
import com.chessplatform.persistence.repository.GameRepository;
import com.chessplatform.persistence.repository.UserRatingRepository;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.rating.GameMode;
import com.chessplatform.rating.GlickoRatingService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Perfiles y clasificación. Consultar (GET) es de lectura pública a propósito, igual
 * que el historial (ver GameHistoryController): ver el perfil o el ranking de
 * cualquiera es normal en cualquier plataforma de ajedrez real. Editar (PUT), cambiar
 * contraseña y borrar la cuenta sí necesitan identidad de verdad — ver SecurityConfig y
 * JwtAuthenticationFilter, que existen justo por este endpoint.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final int MIN_PASSWORD_LENGTH = 8; // misma regla que en el registro, ver AuthController

    private final UserRepository userRepository;
    private final UserRatingRepository userRatingRepository;
    private final GameRepository gameRepository;
    private final PasswordEncoder passwordEncoder;

    // Mutable y con valor por defecto en vez de un segundo constructor — con dos
    // constructores, Spring no sabe cuál autoconectar (ninguno lleva @Autowired) y
    // falla al arrancar con "No default constructor found". Mismo patrón que ya se usa
    // en MatchmakingQueue.setClock(): un único constructor de verdad, y esto solo lo
    // toca un test para no depender del instante exacto en el que corre de verdad.
    private Clock clock = Clock.systemUTC();

    public UserController(UserRepository userRepository, UserRatingRepository userRatingRepository,
                          GameRepository gameRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userRatingRepository = userRatingRepository;
        this.gameRepository = gameRepository;
        this.passwordEncoder = passwordEncoder;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** mode por defecto BLITZ, la misma modalidad que ya viene preseleccionada en el desplegable de "Buscar partida". */
    @GetMapping("/leaderboard")
    public List<LeaderboardEntryResponse> leaderboard(@RequestParam(defaultValue = "BLITZ") String mode) {
        GameMode gameMode;
        try {
            gameMode = GameMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Modalidad desconocida: " + mode);
        }

        List<UserRating> topRatings = userRatingRepository.findTop50ByModeAndUser_DeletedAtIsNullOrderByRatingDesc(gameMode);
        return IntStream.range(0, topRatings.size())
                .mapToObj(i -> {
                    UserRating rating = topRatings.get(i);
                    return new LeaderboardEntryResponse(i + 1, rating.getUser().getId(), rating.getUser().getUsername(),
                            (int) Math.round(rating.getRating()));
                })
                .toList();
    }

    @GetMapping("/{userId}")
    public UserProfileResponse profile(@PathVariable String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
        return toProfileResponse(user);
    }

    /**
     * authentication viene de JwtAuthenticationFilter, que solo puebla el
     * SecurityContext si el JWT es válido — pero cualquiera con un JWT válido podría
     * intentar editar el perfil de OTRO usuario cambiando el {userId} de la URL, así que
     * hace falta comprobar aquí que la identidad autenticada coincide con el perfil que
     * se intenta editar, no basta con exigir "estar autenticado con algo".
     */
    @PutMapping("/{userId}")
    public UserProfileResponse updateProfile(@PathVariable String userId, @RequestBody UpdateProfileRequest request,
                                             Authentication authentication) {
        User user = requireOwnAccount(userId, authentication);

        String newUsername = request.username() == null ? "" : request.username().trim();
        if (newUsername.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre de usuario no puede estar vacío");
        }
        if (!newUsername.equals(user.getUsername()) && userRepository.findByUsername(newUsername).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese nombre de usuario ya está en uso");
        }

        user.updateProfile(newUsername, blankToNull(request.country()), blankToNull(request.avatarUrl()));
        userRepository.save(user);

        return toProfileResponse(user);
    }

    @PutMapping("/{userId}/password")
    public void changePassword(@PathVariable String userId, @RequestBody ChangePasswordRequest request,
                               Authentication authentication) {
        User user = requireOwnAccount(userId, authentication);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La contraseña actual no es correcta");
        }
        if (request.newPassword() == null || request.newPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña nueva debe tener al menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }
        // Cubre a la vez "es igual a la actual" y "es una de las últimas que ya tuviste"
        // — la actual cuenta como una más de esas últimas, ver User.matchesAnyRecentPassword().
        if (user.matchesAnyRecentPassword(request.newPassword(), passwordEncoder)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La contraseña nueva no puede coincidir con ninguna de las últimas 5 que has usado");
        }

        user.changePassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    /**
     * Borrado lógico, no DELETE de verdad — ver el javadoc de User.deletedAt. Exige la
     * contraseña igual que cambiarla: es la acción más destructiva de todas, así que el
     * listón de confirmación no puede ser más bajo que el de un cambio de contraseña
     * normal.
     */
    @DeleteMapping("/{userId}")
    public void deleteAccount(@PathVariable String userId, @RequestBody DeleteAccountRequest request,
                              Authentication authentication) {
        User user = requireOwnAccount(userId, authentication);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La contraseña no es correcta");
        }

        // Primeros 8 caracteres del id (un UUID) bastan para que no choque con nadie,
        // y se leen mejor que el UUID entero si algún día aparecen en algún sitio.
        String anonymizedUsername = "usuario-eliminado-" + user.getId().substring(0, 8);
        // Un hash de verdad de BCrypt, pero de una contraseña aleatoria que nadie
        // conoce — así el login queda descartado sin necesitar un campo "activo"
        // aparte que comprobar en cada sitio (ver AuthController.login()).
        String unusablePasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());

        user.anonymizeForDeletion(anonymizedUsername, unusablePasswordHash, Instant.now(clock));
        userRepository.save(user);
    }

    /**
     * Comparte la comprobación de "¿esta identidad autenticada puede tocar este
     * perfil?" entre los tres endpoints de escritura — mismo motivo que ya se explica
     * en updateProfile().
     */
    private User requireOwnAccount(String userId, Authentication authentication) {
        if (authentication == null || !userId.equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes modificar tu propia cuenta");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    /**
     * Las cuatro modalidades siempre, en el mismo orden — sin pasar por
     * UserRatingService.findOrDefault(): consultar un perfil no debería crear ninguna
     * fila en base de datos, solo devolver los valores por defecto de Glicko-2 para la
     * modalidad que todavía no tenga una fila de verdad.
     */
    private List<UserProfileResponse.ModeRatingResponse> ratingsFor(String userId) {
        return Arrays.stream(GameMode.values())
                .map(mode -> {
                    Optional<UserRating> existing = userRatingRepository.findByUser_IdAndMode(userId, mode);
                    int rating = existing.map(r -> (int) Math.round(r.getRating()))
                            .orElse((int) GlickoRatingService.DEFAULT_RATING);
                    int ratingDeviation = existing.map(r -> (int) Math.round(r.getRatingDeviation()))
                            .orElse((int) GlickoRatingService.DEFAULT_RATING_DEVIATION);
                    return new UserProfileResponse.ModeRatingResponse(mode.name(), rating, ratingDeviation);
                })
                .toList();
    }

    private UserProfileResponse toProfileResponse(User user) {
        List<Game> games = gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc(user.getId(), user.getId());

        int wins = 0;
        int losses = 0;
        int draws = 0;
        int winsByCheckmate = 0;
        for (Game game : games) {
            if ("1/2-1/2".equals(game.getResult())) {
                draws++;
                continue;
            }
            boolean userIsWhite = user.getId().equals(game.getWhitePlayer().getId());
            boolean whiteWon = "1-0".equals(game.getResult());
            boolean userWon = userIsWhite == whiteWon;
            if (userWon) {
                wins++;
                if ("checkmate".equals(game.getReason())) {
                    winsByCheckmate++;
                }
            } else {
                losses++;
            }
        }

        // games ya viene ordenado por fecha descendente (ver la consulta arriba), así
        // que el primer encuentro de cada rival en el recorrido es, precisamente, la
        // partida más reciente contra esa persona — distinct() conserva ese orden de
        // primera aparición.
        List<UserProfileResponse.RecentOpponent> recentOpponents = games.stream()
                .map(game -> {
                    boolean userIsWhite = user.getId().equals(game.getWhitePlayer().getId());
                    User opponent = userIsWhite ? game.getBlackPlayer() : game.getWhitePlayer();
                    return new UserProfileResponse.RecentOpponent(opponent.getId(), opponent.getUsername());
                })
                .distinct()
                .limit(5)
                .toList();

        int winRatePercent = games.isEmpty() ? 0 : (int) Math.round(wins * 100.0 / games.size());

        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getCountry(),
                user.getAvatarUrl(),
                ratingsFor(user.getId()),
                games.size(),
                wins,
                losses,
                draws,
                winsByCheckmate,
                winRatePercent,
                recentOpponents
        );
    }
}
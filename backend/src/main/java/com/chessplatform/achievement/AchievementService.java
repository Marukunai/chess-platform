package com.chessplatform.achievement;

import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.entity.UserRating;
import com.chessplatform.persistence.repository.DirectMessageRepository;
import com.chessplatform.persistence.repository.FriendshipRepository;
import com.chessplatform.persistence.repository.GameRepository;
import com.chessplatform.persistence.repository.UserRatingRepository;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.rating.GameMode;
import com.chessplatform.rating.GlickoRatingService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * El progreso de cada logro se calcula al vuelo a partir de datos que ya existen
 * (partidas, amigos, mensajes, rating) — no hay ninguna tabla de "logros desbloqueados"
 * en la base de datos, ni ningún sitio del código que se entere de "acabas de
 * desbloquear X" en el momento en que pasa. Mismo criterio que ya usa
 * UserController.toProfileResponse() para victorias/derrotas/tablas: derivar de la
 * fuente de verdad en vez de mantener un contador aparte sincronizado a mano.
 *
 * Contrapartida asumida a propósito: el ranking global de logros
 * (AchievementController.leaderboard()) recalcula la foto de CADA usuario activo en
 * cada petición — aceptable a la escala de un proyecto personal, pero no escalaría bien
 * con miles de usuarios concurrentes sin cachear o persistir. Si algún día hiciera
 * falta, sería el primer sitio a optimizar.
 */
@Component
public class AchievementService {

    private final GameRepository gameRepository;
    private final FriendshipRepository friendshipRepository;
    private final DirectMessageRepository directMessageRepository;
    private final UserRatingRepository userRatingRepository;
    private final UserRepository userRepository;

    // Mutable con valor por defecto, no un segundo constructor — dos constructores sin
    // @Autowired hacen que Spring no sepa cuál usar y falla al arrancar con "No default
    // constructor found" (ya nos pasó una vez con MatchmakingQueue). Mismo patrón que
    // UserController.setClock(): un único constructor de verdad, y esto solo lo toca un
    // test para no depender de la fecha real en los logros de antigüedad de cuenta.
    private Clock clock = Clock.systemUTC();

    public AchievementService(GameRepository gameRepository, FriendshipRepository friendshipRepository,
                              DirectMessageRepository directMessageRepository, UserRatingRepository userRatingRepository,
                              UserRepository userRepository) {
        this.gameRepository = gameRepository;
        this.friendshipRepository = friendshipRepository;
        this.directMessageRepository = directMessageRepository;
        this.userRatingRepository = userRatingRepository;
        this.userRepository = userRepository;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public UserStatsSnapshot computeSnapshot(String userId) {
        List<Game> games = gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc(userId, userId);

        int gamesWon = 0;
        int gamesLost = 0;
        int gamesDrawn = 0;
        int stalemateDraws = 0;
        int checkmateWins = 0;
        for (Game game : games) {
            if ("1/2-1/2".equals(game.getResult())) {
                gamesDrawn++;
                if ("stalemate".equals(game.getReason())) {
                    stalemateDraws++;
                }
                continue;
            }
            boolean userIsWhite = userId.equals(game.getWhitePlayer().getId());
            boolean whiteWon = "1-0".equals(game.getResult());
            if (userIsWhite == whiteWon) {
                gamesWon++;
                if ("checkmate".equals(game.getReason())) {
                    checkmateWins++;
                }
            } else {
                gamesLost++;
            }
        }

        int friendsCount = friendshipRepository.findAcceptedFriendships(userId).size();
        long directMessagesSent = directMessageRepository.countBySender_Id(userId);
        long directMessagesReceived = directMessageRepository.countByRecipient_Id(userId);
        long distinctConversationPartners = directMessageRepository.countDistinctConversationPartners(userId);

        List<UserRating> ratings = userRatingRepository.findByUser_Id(userId);
        int highestRating = ratings.stream()
                .mapToInt(r -> (int) Math.round(r.getRating()))
                .max()
                .orElse((int) GlickoRatingService.DEFAULT_RATING);
        Set<GameMode> modesPlayed = ratings.stream().map(UserRating::getMode).collect(Collectors.toSet());

        // Sin fila de User (no debería pasar nunca en la práctica — solo se pide el
        // progreso de cuentas que existen), se cae a "sin perfil rellenado, cuenta
        // recién creada" en vez de reventar el cálculo entero por esto.
        Optional<User> user = userRepository.findById(userId);
        boolean hasAvatarSet = user.map(User::getAvatarUrl).filter(url -> !url.isBlank()).isPresent();
        boolean hasCountrySet = user.map(User::getCountry).filter(country -> !country.isBlank()).isPresent();
        int accountAgeDays = user.map(u -> (int) Duration.between(u.getCreatedAt(), Instant.now(clock)).toDays())
                .orElse(0);

        return new UserStatsSnapshot(games.size(), gamesWon, gamesLost, gamesDrawn, stalemateDraws, checkmateWins,
                friendsCount, (int) directMessagesSent, (int) directMessagesReceived, (int) distinctConversationPartners,
                highestRating, modesPlayed, hasAvatarSet, hasCountrySet, accountAgeDays);
    }

    public List<AchievementProgress> progressFor(String userId) {
        UserStatsSnapshot snapshot = computeSnapshot(userId);
        return AchievementCatalog.ALL.stream()
                .map(def -> new AchievementProgress(def, snapshot))
                .toList();
    }

    public int unlockedCountFor(String userId) {
        UserStatsSnapshot snapshot = computeSnapshot(userId);
        return (int) AchievementCatalog.ALL.stream().filter(def -> def.isUnlockedFor(snapshot)).count();
    }

    /** Para el ranking global — cada usuario activo con cuántos de los 20 logros tiene desbloqueados, de más a menos. */
    public List<UserAchievementCount> leaderboard() {
        return userRepository.findByDeletedAtIsNull().stream()
                .map(user -> new UserAchievementCount(user, unlockedCountFor(user.getId())))
                .sorted(Comparator.comparingInt(UserAchievementCount::unlockedCount).reversed())
                .toList();
    }

    /** Envoltorio de un AchievementDefinition ya evaluado contra una foto concreta — evita recalcular progressFor()/isUnlockedFor() dos veces cada uno al construir la respuesta. */
    public record AchievementProgress(AchievementDefinition definition, UserStatsSnapshot snapshot) {
        public int currentProgress() {
            return definition.progressFor(snapshot);
        }

        public boolean unlocked() {
            return definition.isUnlockedFor(snapshot);
        }
    }

    public record UserAchievementCount(User user, int unlockedCount) {
    }
}
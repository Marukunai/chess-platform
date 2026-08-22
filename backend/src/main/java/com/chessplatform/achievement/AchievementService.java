package com.chessplatform.achievement;

import com.chessplatform.bot.BotAccountSeeder;
import com.chessplatform.bot.BotDifficulty;
import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.entity.UserAchievementUnlock;
import com.chessplatform.persistence.entity.UserRating;
import com.chessplatform.persistence.repository.DirectMessageRepository;
import com.chessplatform.persistence.repository.FriendshipRepository;
import com.chessplatform.persistence.repository.GameRepository;
import com.chessplatform.persistence.repository.UserAchievementUnlockRepository;
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
import java.util.Map;
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

    // Mismo formato que ya construye GameResultRecorder al guardar la partida
    // ("%d+%d" con minutos+segundos) — los cuatro presets nunca cambian, así que esta
    // correspondencia es segura sin tener que reconstruir Duration desde el texto guardado.
    private static final Map<String, GameMode> TIME_CONTROL_LABEL_TO_MODE = Map.of(
            "1+0", GameMode.BULLET,
            "5+3", GameMode.BLITZ,
            "10+5", GameMode.RAPID,
            "30+20", GameMode.CLASSICAL
    );

    private final GameRepository gameRepository;
    private final FriendshipRepository friendshipRepository;
    private final DirectMessageRepository directMessageRepository;
    private final UserRatingRepository userRatingRepository;
    private final UserRepository userRepository;
    private final UserAchievementUnlockRepository unlockRepository;

    // Mutable con valor por defecto, no un segundo constructor — dos constructores sin
    // @Autowired hacen que Spring no sepa cuál usar y falla al arrancar con "No default
    // constructor found" (ya nos pasó una vez con MatchmakingQueue). Mismo patrón que
    // UserController.setClock(): un único constructor de verdad, y esto solo lo toca un
    // test para no depender de la fecha real en los logros de antigüedad de cuenta.
    private Clock clock = Clock.systemUTC();

    public AchievementService(GameRepository gameRepository, FriendshipRepository friendshipRepository,
                              DirectMessageRepository directMessageRepository, UserRatingRepository userRatingRepository,
                              UserRepository userRepository, UserAchievementUnlockRepository unlockRepository) {
        this.gameRepository = gameRepository;
        this.friendshipRepository = friendshipRepository;
        this.directMessageRepository = directMessageRepository;
        this.userRatingRepository = userRatingRepository;
        this.userRepository = userRepository;
        this.unlockRepository = unlockRepository;
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public UserStatsSnapshot computeSnapshot(String userId) {
        List<Game> games = gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc(userId, userId);

        int gamesPlayed = 0; // excluye partidas contra bot a propósito — ver el javadoc de UserStatsSnapshot
        int gamesWon = 0;
        int gamesLost = 0;
        int gamesDrawn = 0;
        int stalemateDraws = 0;
        int checkmateWins = 0;
        int easyBotWins = 0;
        int mediumBotWins = 0;
        int hardBotWins = 0;
        int hardBotBlitzWins = 0;
        int hardBotClassicalWins = 0;

        for (Game game : games) {
            boolean userIsWhite = userId.equals(game.getWhitePlayer().getId());
            User opponent = userIsWhite ? game.getBlackPlayer() : game.getWhitePlayer();
            boolean whiteWon = "1-0".equals(game.getResult());
            boolean isDraw = "1/2-1/2".equals(game.getResult());
            boolean userWon = !isDraw && (userIsWhite == whiteWon);

            if (opponent.isBot()) {
                // Partida de práctica — no cuenta para nada de lo general de arriba
                // (gamesPlayed, gamesWon...), solo para su propia categoría de logros.
                if (userWon) {
                    Optional<BotDifficulty> maybeDifficulty = BotAccountSeeder.difficultyFor(opponent.getUsername());
                    if (maybeDifficulty.isPresent()) {
                        switch (maybeDifficulty.get()) {
                            case EASY -> easyBotWins++;
                            case MEDIUM -> mediumBotWins++;
                            case HARD -> {
                                hardBotWins++;
                                GameMode mode = TIME_CONTROL_LABEL_TO_MODE.get(game.getTimeControl());
                                if (mode == GameMode.BLITZ) {
                                    hardBotBlitzWins++;
                                } else if (mode == GameMode.CLASSICAL) {
                                    hardBotClassicalWins++;
                                }
                            }
                        }
                    }
                }
                continue;
            }

            gamesPlayed++;
            if (isDraw) {
                gamesDrawn++;
                if ("stalemate".equals(game.getReason())) {
                    stalemateDraws++;
                }
            } else if (userWon) {
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

        return new UserStatsSnapshot(gamesPlayed, gamesWon, gamesLost, gamesDrawn, stalemateDraws, checkmateWins,
                friendsCount, (int) directMessagesSent, (int) directMessagesReceived, (int) distinctConversationPartners,
                highestRating, modesPlayed, hasAvatarSet, hasCountrySet, accountAgeDays,
                easyBotWins, mediumBotWins, hardBotWins, hardBotBlitzWins, hardBotClassicalWins);
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

    /** Para el ranking global — cada usuario activo (que no sea un bot) con cuántos logros tiene desbloqueados, de más a menos. */
    public List<UserAchievementCount> leaderboard() {
        return userRepository.findByDeletedAtIsNullAndBotFalse().stream()
                .map(user -> new UserAchievementCount(user, unlockedCountFor(user.getId())))
                .sorted(Comparator.comparingInt(UserAchievementCount::unlockedCount).reversed())
                .toList();
    }

    /**
     * Como progressFor(), pero con tres datos más por logro: cuándo lo desbloqueaste TÚ
     * (null si no lo tienes), qué porcentaje de cuentas activas lo tiene (rareza), y
     * quién fue la primera persona en todo el sistema en conseguirlo — estos tres viven
     * en UserAchievementUnlock (sí persistida, a diferencia del resto de logros, ver su
     * javadoc) porque no se pueden calcular al vuelo sin conocer el momento exacto en
     * que pasó cada desbloqueo.
     *
     * Coste asumido a propósito: hasta dos consultas más POR CADA logro del catálogo
     * (rareza + primero) — igual de aceptable a esta escala que el resto de cálculos al
     * vuelo del servicio, ver el javadoc de la clase.
     */
    public List<DetailedAchievementProgress> detailedProgressFor(String userId) {
        UserStatsSnapshot snapshot = computeSnapshot(userId);
        Map<String, Instant> unlockedAtByAchievementId = unlockRepository.findByUser_Id(userId).stream()
                .collect(Collectors.toMap(UserAchievementUnlock::getAchievementId, UserAchievementUnlock::getUnlockedAt));
        long totalActiveUsers = userRepository.countByDeletedAtIsNullAndBotFalse();

        return AchievementCatalog.ALL.stream()
                .map(def -> {
                    long unlockedByCount = unlockRepository.countByAchievementIdAndUser_DeletedAtIsNull(def.id());
                    double rarityPercent = totalActiveUsers > 0
                            ? Math.round(unlockedByCount * 1000.0 / totalActiveUsers) / 10.0
                            : 0.0;
                    String firstUnlockedByUsername = unlockRepository.findFirstByAchievementIdOrderByUnlockedAtAsc(def.id())
                            .map(u -> u.getUser().getUsername())
                            .orElse(null);
                    return new DetailedAchievementProgress(def, snapshot,
                            unlockedAtByAchievementId.get(def.id()), rarityPercent, firstUnlockedByUsername);
                })
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

    /** Como AchievementProgress, con los tres datos extra de detailedProgressFor() — ver su javadoc. */
    public record DetailedAchievementProgress(AchievementDefinition definition, UserStatsSnapshot snapshot,
                                              Instant unlockedAt, double rarityPercent, String firstUnlockedByUsername) {
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
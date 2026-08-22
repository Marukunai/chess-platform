package com.chessplatform.bot;

import com.chessplatform.bot.dto.PlayVsBotRequest;
import com.chessplatform.engine.Color;
import com.chessplatform.matchmaking.TimeControl;
import com.chessplatform.matchmaking.dto.MatchFoundMessage;
import com.chessplatform.matchmaking.dto.MatchmakingJoinMessage;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
import com.chessplatform.realtime.dto.ErrorMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Random;

/**
 * Punto de entrada STOMP para empezar una partida contra un bot — a diferencia del
 * emparejamiento normal (cola + espera) o de retar a un amigo (propuesta + aceptación),
 * esto crea la partida al instante: no hay nadie al otro lado a quien esperar.
 *
 * Reutiliza GameSession/GameSessionRegistry tal cual (el bot es un User real, ver
 * BotAccountSeeder) — lo único específico de bots es arrancar el motor y registrarlo en
 * BotGameRegistry para que BotMoveService sepa que esta partida necesita su
 * intervención.
 */
@Controller
public class PlayVsBotController {

    private static final Logger log = LoggerFactory.getLogger(PlayVsBotController.class);

    private final GameSessionRegistry sessionRegistry;
    private final BotGameRegistry botGameRegistry;
    private final BotMoveService botMoveService;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final StockfishEngineFactory engineFactory;
    private final String stockfishPath;
    private final Random random = new Random();

    public PlayVsBotController(GameSessionRegistry sessionRegistry, BotGameRegistry botGameRegistry,
                               BotMoveService botMoveService, UserRepository userRepository,
                               SimpMessagingTemplate messagingTemplate, StockfishEngineFactory engineFactory,
                               @Value("${stockfish.path:}") String stockfishPath) {
        this.sessionRegistry = sessionRegistry;
        this.botGameRegistry = botGameRegistry;
        this.botMoveService = botMoveService;
        this.userRepository = userRepository;
        this.messagingTemplate = messagingTemplate;
        this.engineFactory = engineFactory;
        this.stockfishPath = stockfishPath;
    }

    @MessageMapping("/bot/play")
    public void play(PlayVsBotRequest request, Principal principal) {
        if (principal == null) {
            return;
        }
        String humanId = principal.getName();

        if (stockfishPath == null || stockfishPath.isBlank()) {
            sendError(humanId, "STOCKFISH_NOT_CONFIGURED", "El servidor no tiene Stockfish configurado");
            return;
        }

        BotDifficulty difficulty;
        try {
            difficulty = BotDifficulty.valueOf(request.difficulty().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            sendError(humanId, "INVALID_DIFFICULTY", "Dificultad desconocida: " + request.difficulty());
            return;
        }

        TimeControl timeControl;
        try {
            timeControl = new MatchmakingJoinMessage(request.timeControlPreset()).toTimeControl();
        } catch (IllegalArgumentException e) {
            sendError(humanId, "INVALID_TIME_CONTROL", e.getMessage());
            return;
        }

        User botUser = userRepository.findByUsername(BotAccountSeeder.usernameFor(difficulty)).orElse(null);
        if (botUser == null) {
            // Solo debería pasar en el primerísimo instante tras arrancar el backend,
            // antes de que BotAccountSeeder haya terminado — ver su javadoc.
            sendError(humanId, "BOT_NOT_AVAILABLE", "La cuenta del bot todavía no existe — inténtalo de nuevo en unos segundos");
            return;
        }

        StockfishEngine engine;
        try {
            engine = engineFactory.create(stockfishPath);
            engine.setSkillLevel(difficulty.skillLevel());
        } catch (Exception e) {
            log.error("No se pudo arrancar Stockfish para la partida de {}", humanId, e);
            sendError(humanId, "ENGINE_UNAVAILABLE", "No se pudo iniciar el motor — inténtalo de nuevo");
            return;
        }

        boolean humanIsWhite = resolveHumanColor(request.color());
        String whitePlayerId = humanIsWhite ? humanId : botUser.getId();
        String blackPlayerId = humanIsWhite ? botUser.getId() : humanId;
        Color botColor = humanIsWhite ? Color.BLACK : Color.WHITE;

        User human = userRepository.findById(humanId).orElse(null);
        String humanUsername = human != null ? human.getUsername() : humanId;
        String humanAvatarUrl = human != null ? human.getAvatarUrl() : null;

        GameSession session = new GameSession(whitePlayerId, blackPlayerId, timeControl.initialTime(), timeControl.increment());
        session.setUsernames(
                humanIsWhite ? humanUsername : botUser.getUsername(),
                humanIsWhite ? botUser.getUsername() : humanUsername
        );
        session.setAvatars(
                humanIsWhite ? humanAvatarUrl : null,
                humanIsWhite ? null : humanAvatarUrl
        );
        sessionRegistry.create(session);
        botGameRegistry.register(session.gameId(), new BotGameInfo(engine, botColor, difficulty));

        messagingTemplate.convertAndSend(
                "/topic/user/%s".formatted(humanId),
                new MatchFoundMessage(session.gameId(), humanIsWhite ? "white" : "black"));

        // Si el bot juega blancas, le toca mover a él primero — no hay ninguna jugada
        // humana previa que lo dispare sola, hay que pedírselo aquí mismo.
        botMoveService.maybeTriggerBotMove(session);
    }

    /** "white"/"black" tal cual; cualquier otra cosa (incluido "random" o null) se trata como "al azar". */
    private boolean resolveHumanColor(String requestedColor) {
        if ("white".equalsIgnoreCase(requestedColor)) {
            return true;
        }
        if ("black".equalsIgnoreCase(requestedColor)) {
            return false;
        }
        return random.nextBoolean();
    }

    private void sendError(String userId, String code, String message) {
        messagingTemplate.convertAndSend(
                "/topic/user/%s".formatted(userId),
                new ErrorMessage(code, message)
        );
    }
}
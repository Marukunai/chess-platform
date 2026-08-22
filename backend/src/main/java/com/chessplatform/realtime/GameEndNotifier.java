package com.chessplatform.realtime;

import com.chessplatform.achievement.AchievementUnlockService;
import com.chessplatform.bot.BotGameInfo;
import com.chessplatform.bot.BotGameRegistry;
import com.chessplatform.engine.Color;
import com.chessplatform.matchmaking.TimeControl;
import com.chessplatform.presence.PresenceService;
import com.chessplatform.rating.GameResultRecorder;
import com.chessplatform.rating.GameResultRecorder.RatingChanges;
import com.chessplatform.realtime.dto.GameOverMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Único punto por el que se anuncia el fin de una partida, se registra su resultado
 * (rating + historial, ver GameResultRecorder) y se limpia del registro — usado desde
 * GameWebSocketController (jaque mate, ahogado, rendición) y desde GameTimeoutService
 * (bandera caída), para que las cuatro formas de terminar una partida pasen siempre por
 * la misma lógica.
 *
 * Partidas contra un bot: también cierra el proceso de Stockfish asociado (ver
 * BotGameRegistry.remove()) — si esto no se hiciera aquí, el proceso se quedaría
 * huérfano corriendo indefinidamente cada vez que alguien termina una partida contra el
 * bot. Y ni la presencia ni los logros se comprueban para el lado del bot: no tiene
 * amigos a los que avisar, ni tiene sentido evaluarle el catálogo de logros a una cuenta
 * que no es una persona.
 */
@Component
public class GameEndNotifier {

    private static final Logger log = LoggerFactory.getLogger(GameEndNotifier.class);

    private final GameSessionRegistry sessionRegistry;
    private final SimpMessagingTemplate messagingTemplate;
    private final GameResultRecorder gameResultRecorder;
    private final PresenceService presenceService;
    private final AchievementUnlockService achievementUnlockService;
    private final BotGameRegistry botGameRegistry;

    public GameEndNotifier(GameSessionRegistry sessionRegistry, SimpMessagingTemplate messagingTemplate,
                           GameResultRecorder gameResultRecorder, PresenceService presenceService,
                           AchievementUnlockService achievementUnlockService, BotGameRegistry botGameRegistry) {
        this.sessionRegistry = sessionRegistry;
        this.messagingTemplate = messagingTemplate;
        this.gameResultRecorder = gameResultRecorder;
        this.presenceService = presenceService;
        this.achievementUnlockService = achievementUnlockService;
        this.botGameRegistry = botGameRegistry;
    }

    /**
     * @param result "1-0" | "0-1" | "1/2-1/2"
     * @param reason "checkmate" | "stalemate" | "resignation" | "timeout"
     */
    public void endGame(GameSession session, String result, String reason) {
        Optional<RatingChanges> ratingChanges = Optional.empty();
        try {
            ratingChanges = gameResultRecorder.record(session, result, reason);
        } catch (RuntimeException e) {
            // Un fallo al guardar (p. ej. la base de datos caída) no debería impedir que
            // los jugadores se enteren de que la partida ha terminado — es peor dejarlos
            // esperando indefinidamente que perder el guardado de esta partida concreta.
            log.error("No se pudo registrar el resultado de la partida {}", session.gameId(), e);
        }

        String timeControlPreset = TimeControl.presetNameFor(session.initialTime(), session.increment())
                .orElse(null);

        messagingTemplate.convertAndSend(
                "/topic/game/%s".formatted(session.gameId()),
                new GameOverMessage(
                        session.gameId(), result, reason,
                        ratingChanges.map(RatingChanges::whiteChange).orElse(null),
                        ratingChanges.map(RatingChanges::blackChange).orElse(null),
                        session.whitePlayerId(), session.whiteUsername(),
                        session.blackPlayerId(), session.blackUsername(),
                        timeControlPreset
                )
        );
        sessionRegistry.remove(session.gameId());

        // Hay que consultar el registro de bots ANTES de limpiarlo (remove() olvida la
        // entrada) — así se sabe, si esto era una partida contra bot, cuál de los dos
        // lados es el bot antes de que esa información desaparezca junto con el proceso.
        Optional<BotGameInfo> maybeBotInfo = botGameRegistry.find(session.gameId());
        botGameRegistry.remove(session.gameId()); // cierra el proceso de Stockfish si lo había; no hace nada si no

        String botPlayerId = maybeBotInfo
                .map(info -> info.botColor() == Color.WHITE ? session.whitePlayerId() : session.blackPlayerId())
                .orElse(null); // partida normal entre humanos — ningún playerId real coincidirá nunca con null

        // Después de quitarla del registro, no antes — statusOf() decide "en partida"
        // mirando si sigue habiendo una sesión activa para este jugador, así que hay
        // que avisar a los amigos justo después de que deje de haberla.
        if (!session.whitePlayerId().equals(botPlayerId)) {
            presenceService.notifyFriendsOfStatusChange(session.whitePlayerId());
        }
        if (!session.blackPlayerId().equals(botPlayerId)) {
            presenceService.notifyFriendsOfStatusChange(session.blackPlayerId());
        }

        // Al final de todo — con la partida ya guardada, el rating actualizado y todo
        // lo demás resuelto, es el momento correcto para comprobar si esto acaba de
        // desbloquear algún logro nuevo (partidas jugadas, victorias, jaque mate,
        // rating, modalidad...) para cada uno de los dos jugadores, salvo el que sea el bot.
        if (!session.whitePlayerId().equals(botPlayerId)) {
            achievementUnlockService.checkAndNotify(session.whitePlayerId());
        }
        if (!session.blackPlayerId().equals(botPlayerId)) {
            achievementUnlockService.checkAndNotify(session.blackPlayerId());
        }
    }
}
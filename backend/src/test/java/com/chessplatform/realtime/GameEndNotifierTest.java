package com.chessplatform.realtime;

import com.chessplatform.achievement.AchievementUnlockService;
import com.chessplatform.bot.BotDifficulty;
import com.chessplatform.bot.BotGameInfo;
import com.chessplatform.bot.BotGameRegistry;
import com.chessplatform.bot.StockfishEngine;
import com.chessplatform.engine.Color;
import com.chessplatform.presence.PresenceService;
import com.chessplatform.rating.GameResultRecorder;
import com.chessplatform.rating.GameResultRecorder.RatingChanges;
import com.chessplatform.realtime.dto.GameOverMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameEndNotifierTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private GameResultRecorder gameResultRecorder;

    @Mock
    private PresenceService presenceService;

    @Mock
    private AchievementUnlockService achievementUnlockService;

    @Mock
    private StockfishEngine engine;

    private GameSessionRegistry sessionRegistry;
    private BotGameRegistry botGameRegistry;
    private GameEndNotifier notifier;

    @BeforeEach
    void setUp() {
        sessionRegistry = new GameSessionRegistry();
        // BotGameRegistry REAL (no mock), vacío por defecto — para todos los tests que
        // ya existían (partidas normales entre humanos), esto se comporta exactamente
        // igual que antes de que existieran los bots, sin tener que tocar ni una
        // aserción. Los tests nuevos de más abajo sí lo usan de verdad, registrando una
        // partida contra bot antes de llamar a endGame().
        botGameRegistry = new BotGameRegistry();
        notifier = new GameEndNotifier(sessionRegistry, messagingTemplate, gameResultRecorder, presenceService,
                achievementUnlockService, botGameRegistry);
    }

    private static GameSession newSession() {
        return new GameSession("white-player", "black-player", Duration.ofMinutes(10), Duration.ZERO);
    }

    @Test
    void endGameBroadcastsGameOverAndRemovesTheSessionFromTheRegistry() {
        GameSession session = newSession();
        sessionRegistry.create(session);

        notifier.endGame(session, "1-0", "checkmate");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(GameOverMessage.class);

        GameOverMessage gameOver = (GameOverMessage) payload.getValue();
        assertThat(gameOver.result()).isEqualTo("1-0");
        assertThat(gameOver.reason()).isEqualTo("checkmate");

        assertThat(sessionRegistry.find(session.gameId())).isEmpty();
    }

    @Test
    void endGameRecordsTheResultForRatingAndHistory() {
        GameSession session = newSession();
        sessionRegistry.create(session);

        notifier.endGame(session, "1-0", "checkmate");

        verify(gameResultRecorder).record(session, "1-0", "checkmate");
    }

    @Test
    void endGameStillBroadcastsAndCleansUpEvenIfRecordingTheResultFails() {
        GameSession session = newSession();
        sessionRegistry.create(session);
        doThrow(new RuntimeException("base de datos caída")).when(gameResultRecorder).record(session, "1-0", "checkmate");

        notifier.endGame(session, "1-0", "checkmate");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(GameOverMessage.class);
        assertThat(sessionRegistry.find(session.gameId())).isEmpty();
    }

    @Test
    void endGameIncludesTheRatingChangesFromGameResultRecorderInTheBroadcast() {
        GameSession session = newSession();
        sessionRegistry.create(session);
        when(gameResultRecorder.record(session, "1-0", "checkmate")).thenReturn(Optional.of(new RatingChanges(12.5, -12.5)));

        notifier.endGame(session, "1-0", "checkmate");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        GameOverMessage gameOver = (GameOverMessage) payload.getValue();
        assertThat(gameOver.whiteRatingChange()).isEqualTo(12.5);
        assertThat(gameOver.blackRatingChange()).isEqualTo(-12.5);
    }

    @Test
    void endGameSendsNullRatingChangesWhenRecordingFails() {
        GameSession session = newSession();
        sessionRegistry.create(session);
        doThrow(new RuntimeException("base de datos caída")).when(gameResultRecorder).record(session, "1-0", "checkmate");

        notifier.endGame(session, "1-0", "checkmate");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        GameOverMessage gameOver = (GameOverMessage) payload.getValue();
        assertThat(gameOver.whiteRatingChange()).isNull();
        assertThat(gameOver.blackRatingChange()).isNull();
    }

    @Test
    void endGameIncludesPlayersAndTimeControlPresetForAPossibleRematch() {
        GameSession session = newSession(); // 10min+0 == ningún preset conocido a propósito, ver el siguiente test
        session.setUsernames("alice", "bob");
        sessionRegistry.create(session);

        notifier.endGame(session, "1-0", "checkmate");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        GameOverMessage gameOver = (GameOverMessage) payload.getValue();
        assertThat(gameOver.whitePlayerId()).isEqualTo("white-player");
        assertThat(gameOver.whiteUsername()).isEqualTo("alice");
        assertThat(gameOver.blackPlayerId()).isEqualTo("black-player");
        assertThat(gameOver.blackUsername()).isEqualTo("bob");
        assertThat(gameOver.timeControlPreset()).isNull(); // 10min+0 no es ninguno de los cuatro presets
    }

    @Test
    void endGameResolvesTheTimeControlPresetWhenItMatchesAKnownOne() {
        GameSession blitzSession = new GameSession("white-player", "black-player",
                java.time.Duration.ofMinutes(5), java.time.Duration.ofSeconds(3)); // == TimeControl.BLITZ
        sessionRegistry.create(blitzSession);

        notifier.endGame(blitzSession, "1-0", "checkmate");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + blitzSession.gameId()), payload.capture());
        assertThat(((GameOverMessage) payload.getValue()).timeControlPreset()).isEqualTo("BLITZ");
    }

    @Test
    void endGameNotifiesFriendsOfBothPlayersThatTheyAreNoLongerInAGame() {
        GameSession session = newSession();
        sessionRegistry.create(session);

        notifier.endGame(session, "1-0", "checkmate");

        verify(presenceService).notifyFriendsOfStatusChange("white-player");
        verify(presenceService).notifyFriendsOfStatusChange("black-player");
    }

    @Test
    void endGameChecksAchievementsForBothPlayers() {
        GameSession session = newSession();
        sessionRegistry.create(session);

        notifier.endGame(session, "1-0", "checkmate");

        verify(achievementUnlockService).checkAndNotify("white-player");
        verify(achievementUnlockService).checkAndNotify("black-player");
    }

    @Test
    void endGameClosesTheEngineWhenItWasAGameAgainstABot() {
        GameSession session = newSession();
        sessionRegistry.create(session);
        botGameRegistry.register(session.gameId(), new BotGameInfo(engine, Color.BLACK, BotDifficulty.EASY));

        notifier.endGame(session, "1-0", "checkmate");

        verify(engine).close();
        assertThat(botGameRegistry.find(session.gameId())).isEmpty();
    }

    @Test
    void endGameDoesNotNotifyPresenceForTheBotSide() {
        GameSession session = newSession(); // black-player es el bot en este montaje
        sessionRegistry.create(session);
        botGameRegistry.register(session.gameId(), new BotGameInfo(engine, Color.BLACK, BotDifficulty.EASY));

        notifier.endGame(session, "1-0", "checkmate");

        verify(presenceService).notifyFriendsOfStatusChange("white-player");
        verify(presenceService, never()).notifyFriendsOfStatusChange("black-player");
    }

    @Test
    void endGameDoesNotCheckAchievementsForTheBotSide() {
        GameSession session = newSession();
        sessionRegistry.create(session);
        botGameRegistry.register(session.gameId(), new BotGameInfo(engine, Color.BLACK, BotDifficulty.EASY));

        notifier.endGame(session, "1-0", "checkmate");

        verify(achievementUnlockService).checkAndNotify("white-player");
        verify(achievementUnlockService, never()).checkAndNotify("black-player");
    }

    @Test
    void endGameStillNotifiesTheHumanNormallyEvenAgainstABot() {
        GameSession session = newSession();
        sessionRegistry.create(session);
        botGameRegistry.register(session.gameId(), new BotGameInfo(engine, Color.WHITE, BotDifficulty.HARD)); // ahora el bot es blancas

        notifier.endGame(session, "0-1", "checkmate");

        verify(presenceService).notifyFriendsOfStatusChange("black-player");
        verify(presenceService, never()).notifyFriendsOfStatusChange("white-player");
        verify(achievementUnlockService).checkAndNotify("black-player");
        verify(achievementUnlockService, never()).checkAndNotify("white-player");
    }
}
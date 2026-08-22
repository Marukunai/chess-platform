package com.chessplatform.bot;

import com.chessplatform.bot.dto.PlayVsBotRequest;
import com.chessplatform.engine.Color;
import com.chessplatform.matchmaking.dto.MatchFoundMessage;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
import com.chessplatform.realtime.dto.ErrorMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlayVsBotControllerTest {

    @Mock
    private BotMoveService botMoveService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private StockfishEngineFactory engineFactory;

    @Mock
    private StockfishEngine engine;

    private GameSessionRegistry sessionRegistry;
    private BotGameRegistry botGameRegistry;

    private static Principal principalFor(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private static void setId(User user, String id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private PlayVsBotController newController(String stockfishPath) {
        sessionRegistry = new GameSessionRegistry();
        botGameRegistry = new BotGameRegistry();
        return new PlayVsBotController(sessionRegistry, botGameRegistry, botMoveService, userRepository,
                messagingTemplate, engineFactory, stockfishPath);
    }

    private void givenEasyBotAccountExists() throws IOException {
        User bot = new User(BotAccountSeeder.usernameFor(BotDifficulty.EASY), "hash");
        setId(bot, "bot-easy-id");
        bot.markAsBot();
        when(userRepository.findByUsername(BotAccountSeeder.usernameFor(BotDifficulty.EASY))).thenReturn(Optional.of(bot));
        when(engineFactory.create(anyString())).thenReturn(engine);
    }

    // Pequeño atajo — anyString() choca de nombre con el import estático de Mockito si
    // se usa tal cual como nombre de método local, así que este envoltorio evita esa
    // colisión sin tener que cualificar cada uso.
    private static String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }

    @Test
    void playDoesNothingWhenThereIsNoPrincipal() {
        PlayVsBotController controller = newController("/usr/bin/stockfish");

        controller.play(new PlayVsBotRequest("EASY", "white", "BLITZ"), null);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void playSendsErrorWhenStockfishIsNotConfigured() {
        PlayVsBotController controller = newController(""); // vacío == sin configurar

        controller.play(new PlayVsBotRequest("EASY", "white", "BLITZ"), principalFor("human-id"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/human-id"), payload.capture());
        assertThat(((ErrorMessage) payload.getValue()).code()).isEqualTo("STOCKFISH_NOT_CONFIGURED");
    }

    @Test
    void playSendsErrorForAnUnknownDifficulty() {
        PlayVsBotController controller = newController("/usr/bin/stockfish");

        controller.play(new PlayVsBotRequest("IMPOSIBLE", "white", "BLITZ"), principalFor("human-id"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/human-id"), payload.capture());
        assertThat(((ErrorMessage) payload.getValue()).code()).isEqualTo("INVALID_DIFFICULTY");
    }

    @Test
    void playSendsErrorForAnUnknownTimeControl() {
        PlayVsBotController controller = newController("/usr/bin/stockfish");

        controller.play(new PlayVsBotRequest("EASY", "white", "ULTRA-INVENTADO"), principalFor("human-id"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/human-id"), payload.capture());
        assertThat(((ErrorMessage) payload.getValue()).code()).isEqualTo("INVALID_TIME_CONTROL");
    }

    @Test
    void playSendsErrorWhenTheBotAccountDoesNotExistYet() {
        PlayVsBotController controller = newController("/usr/bin/stockfish");
        when(userRepository.findByUsername(BotAccountSeeder.usernameFor(BotDifficulty.EASY))).thenReturn(Optional.empty());

        controller.play(new PlayVsBotRequest("EASY", "white", "BLITZ"), principalFor("human-id"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/human-id"), payload.capture());
        assertThat(((ErrorMessage) payload.getValue()).code()).isEqualTo("BOT_NOT_AVAILABLE");
    }

    @Test
    void playSendsErrorWhenTheEngineFailsToStart() throws IOException {
        PlayVsBotController controller = newController("/usr/bin/stockfish");
        User bot = new User(BotAccountSeeder.usernameFor(BotDifficulty.EASY), "hash");
        setId(bot, "bot-easy-id");
        when(userRepository.findByUsername(BotAccountSeeder.usernameFor(BotDifficulty.EASY))).thenReturn(Optional.of(bot));
        when(engineFactory.create(anyString())).thenThrow(new IOException("binario no encontrado"));

        controller.play(new PlayVsBotRequest("EASY", "white", "BLITZ"), principalFor("human-id"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/human-id"), payload.capture());
        assertThat(((ErrorMessage) payload.getValue()).code()).isEqualTo("ENGINE_UNAVAILABLE");
        assertThat(sessionRegistry.activeCount()).isZero();
    }

    @Test
    void playCreatesASessionWithTheHumanAsWhiteWhenRequested() throws IOException {
        PlayVsBotController controller = newController("/usr/bin/stockfish");
        givenEasyBotAccountExists();

        controller.play(new PlayVsBotRequest("EASY", "white", "BLITZ"), principalFor("human-id"));

        assertThat(sessionRegistry.activeCount()).isEqualTo(1);
        GameSession session = sessionRegistry.allSessions().iterator().next();
        assertThat(session.whitePlayerId()).isEqualTo("human-id");
        assertThat(session.blackPlayerId()).isEqualTo("bot-easy-id");
    }

    @Test
    void playCreatesASessionWithTheHumanAsBlackWhenRequested() throws IOException {
        PlayVsBotController controller = newController("/usr/bin/stockfish");
        givenEasyBotAccountExists();

        controller.play(new PlayVsBotRequest("EASY", "black", "BLITZ"), principalFor("human-id"));

        GameSession session = sessionRegistry.allSessions().iterator().next();
        assertThat(session.blackPlayerId()).isEqualTo("human-id");
        assertThat(session.whitePlayerId()).isEqualTo("bot-easy-id");
    }

    @Test
    void playRegistersTheGameInBotGameRegistryWithTheCorrectBotColor() throws IOException {
        PlayVsBotController controller = newController("/usr/bin/stockfish");
        givenEasyBotAccountExists();

        controller.play(new PlayVsBotRequest("EASY", "white", "BLITZ"), principalFor("human-id"));

        GameSession session = sessionRegistry.allSessions().iterator().next();
        Optional<BotGameInfo> info = botGameRegistry.find(session.gameId());
        assertThat(info).isPresent();
        assertThat(info.get().botColor()).isEqualTo(Color.BLACK); // el humano pidió blancas, el bot juega negras
        assertThat(info.get().difficulty()).isEqualTo(BotDifficulty.EASY);
    }

    @Test
    void playNotifiesOnlyTheHumanNeverTheBotAccount() throws IOException {
        PlayVsBotController controller = newController("/usr/bin/stockfish");
        givenEasyBotAccountExists();

        controller.play(new PlayVsBotRequest("EASY", "white", "BLITZ"), principalFor("human-id"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/user/human-id"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(MatchFoundMessage.class);
        assertThat(((MatchFoundMessage) payload.getValue()).color()).isEqualTo("white");
        verify(messagingTemplate, never()).convertAndSend(eq("/topic/user/bot-easy-id"), any(Object.class));
    }

    @Test
    void playAsksBotMoveServiceToCheckAfterCreatingTheSession() throws IOException {
        PlayVsBotController controller = newController("/usr/bin/stockfish");
        givenEasyBotAccountExists();

        controller.play(new PlayVsBotRequest("EASY", "black", "BLITZ"), principalFor("human-id"));

        // El humano pidió negras, así que el bot juega blancas y le toca mover primero
        // — sin esto, la partida se quedaría esperando para siempre.
        GameSession session = sessionRegistry.allSessions().iterator().next();
        verify(botMoveService).maybeTriggerBotMove(session);
    }

    @Test
    void playConfiguresTheEnginesSkillLevelForTheChosenDifficulty() throws IOException {
        PlayVsBotController controller = newController("/usr/bin/stockfish");
        givenEasyBotAccountExists();

        controller.play(new PlayVsBotRequest("EASY", "white", "BLITZ"), principalFor("human-id"));

        verify(engine).setSkillLevel(BotDifficulty.EASY.skillLevel());
    }
}
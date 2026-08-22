package com.chessplatform.bot;

import com.chessplatform.engine.Color;
import com.chessplatform.engine.Move;
import com.chessplatform.engine.Square;
import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
import com.chessplatform.realtime.GameStateBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotMoveServiceTest {

    @Mock
    private StockfishEngine engine;

    @Mock
    private GameStateBroadcaster gameStateBroadcaster;

    private BotGameRegistry botGameRegistry;
    private GameSessionRegistry sessionRegistry;
    private BotMoveService botMoveService;

    @BeforeEach
    void setUp() {
        botGameRegistry = new BotGameRegistry();
        sessionRegistry = new GameSessionRegistry();
        botMoveService = new BotMoveService(botGameRegistry, sessionRegistry, gameStateBroadcaster);
    }

    private static GameSession newSession() {
        return new GameSession("human-id", "bot-id", Duration.ofMinutes(5), Duration.ofSeconds(3));
    }

    @Test
    void maybeTriggerBotMoveDoesNothingWhenTheGameIsNotAgainstABot() throws Exception {
        GameSession session = newSession();
        sessionRegistry.create(session);
        // Sin registrar en botGameRegistry — es una partida normal entre humanos.

        botMoveService.maybeTriggerBotMove(session);

        verify(engine, never()).bestMove(anyString(), anyInt());
        verify(gameStateBroadcaster, never()).broadcastAndCheckEnd(any());
    }

    @Test
    void maybeTriggerBotMoveDoesNothingWhenItIsNotTheBotsTurnYet() throws Exception {
        GameSession session = newSession(); // recién creada, le toca a blancas (el humano en este montaje)
        sessionRegistry.create(session);
        botGameRegistry.register(session.gameId(), new BotGameInfo(engine, Color.BLACK, BotDifficulty.EASY));

        botMoveService.maybeTriggerBotMove(session);

        verify(engine, never()).bestMove(anyString(), anyInt());
    }

    @Test
    void maybeTriggerBotMoveDoesNothingWhenTheGameAlreadyEndedWithTheHumansMove() throws Exception {
        GameSession session = newSession();
        // A propósito NO se registra en sessionRegistry — simula que GameEndNotifier ya
        // la quitó de ahí porque la jugada del humano terminó la partida sola (jaque
        // mate, tablas automáticas...).
        session.applyMove(new Move(Square.of(4, 1), Square.of(4, 3))); // e2-e4, ahora le toca a negras
        botGameRegistry.register(session.gameId(), new BotGameInfo(engine, Color.BLACK, BotDifficulty.EASY));

        botMoveService.maybeTriggerBotMove(session);

        verify(engine, never()).bestMove(anyString(), anyInt());
    }

    @Test
    void maybeTriggerBotMoveAppliesTheEnginesMoveAndBroadcasts() throws Exception {
        GameSession session = newSession();
        session.applyMove(new Move(Square.of(4, 1), Square.of(4, 3))); // e2-e4, ahora le toca a negras
        sessionRegistry.create(session);
        botGameRegistry.register(session.gameId(), new BotGameInfo(engine, Color.BLACK, BotDifficulty.EASY));
        when(engine.bestMove(anyString(), eq(BotDifficulty.EASY.moveTimeMs())))
                .thenReturn("e7e5");

        botMoveService.maybeTriggerBotMove(session);

        assertThat(session.board().turn()).isEqualTo(Color.WHITE); // la jugada del bot ya se aplicó, vuelve a tocarle a blancas
        verify(gameStateBroadcaster).broadcastAndCheckEnd(session);
    }

    @Test
    void maybeTriggerBotMoveDoesNothingWhenTheEngineFails() throws Exception {
        GameSession session = newSession();
        session.applyMove(new Move(Square.of(4, 1), Square.of(4, 3)));
        sessionRegistry.create(session);
        botGameRegistry.register(session.gameId(), new BotGameInfo(engine, Color.BLACK, BotDifficulty.EASY));
        when(engine.bestMove(anyString(), anyInt()))
                .thenThrow(new IOException("Stockfish no responde"));

        // No debería propagar la excepción — un fallo del proceso externo no debería
        // reventar el hilo que procesaba la jugada humana.
        botMoveService.maybeTriggerBotMove(session);

        verify(gameStateBroadcaster, never()).broadcastAndCheckEnd(any());
    }

    @Test
    void maybeTriggerBotMoveDoesNothingWhenTheEngineReturnsNoMove() throws Exception {
        GameSession session = newSession();
        session.applyMove(new Move(Square.of(4, 1), Square.of(4, 3)));
        sessionRegistry.create(session);
        botGameRegistry.register(session.gameId(), new BotGameInfo(engine, Color.BLACK, BotDifficulty.EASY));
        when(engine.bestMove(anyString(), anyInt()))
                .thenReturn(null);

        botMoveService.maybeTriggerBotMove(session);

        verify(gameStateBroadcaster, never()).broadcastAndCheckEnd(any());
    }

    @Test
    void maybeTriggerBotMoveDoesNothingWhenTheEngineProposesAnIllegalMove() throws Exception {
        GameSession session = newSession();
        session.applyMove(new Move(Square.of(4, 1), Square.of(4, 3)));
        sessionRegistry.create(session);
        botGameRegistry.register(session.gameId(), new BotGameInfo(engine, Color.BLACK, BotDifficulty.EASY));
        // e2e4 no es una jugada legal para las negras aquí (esa casilla ya la ocupa un peón blanco que se movió)
        when(engine.bestMove(anyString(), anyInt()))
                .thenReturn("e2e4");

        botMoveService.maybeTriggerBotMove(session);

        assertThat(session.board().turn()).isEqualTo(Color.BLACK); // no se aplicó nada, sigue siendo su turno
        verify(gameStateBroadcaster, never()).broadcastAndCheckEnd(any());
    }

    @Test
    void maybeTriggerBotMoveUsesTheDifficultysMoveTime() throws Exception {
        GameSession session = newSession();
        session.applyMove(new Move(Square.of(4, 1), Square.of(4, 3)));
        sessionRegistry.create(session);
        botGameRegistry.register(session.gameId(), new BotGameInfo(engine, Color.BLACK, BotDifficulty.HARD));
        when(engine.bestMove(anyString(), eq(BotDifficulty.HARD.moveTimeMs())))
                .thenReturn("e7e5");

        botMoveService.maybeTriggerBotMove(session);

        verify(engine).bestMove(anyString(), eq(BotDifficulty.HARD.moveTimeMs()));
    }
}
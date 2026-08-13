package com.chessplatform.realtime.controller;

import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
import com.chessplatform.realtime.dto.ErrorMessage;
import com.chessplatform.realtime.dto.GameStateSyncMessage;
import com.chessplatform.realtime.dto.MoveMessage;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests con Mockito, sin contexto de Spring — no hace falta levantar un servidor
 * WebSocket real para comprobar que el controlador toma las decisiones correctas
 * (validar, aplicar, difundir), solo verificar cómo llama a sus dos colaboradores.
 */
@ExtendWith(MockitoExtension.class)
class GameWebSocketControllerTest {

    @Mock
    private GameSessionRegistry sessionRegistry;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private GameWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new GameWebSocketController(sessionRegistry, messagingTemplate);
    }

    private static GameSession newSession() {
        return new GameSession("white-player", "black-player", Duration.ofMinutes(10), Duration.ZERO);
    }

    @Test
    void handleMoveSendsErrorWhenGameDoesNotExist() {
        when(sessionRegistry.find("missing-game")).thenReturn(Optional.empty());

        controller.handleMove("missing-game", new MoveMessage("missing-game", "e2", "e4", null));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/missing-game"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(ErrorMessage.class);
        assertThat(((ErrorMessage) payload.getValue()).code()).isEqualTo("GAME_NOT_FOUND");
    }

    @Test
    void handleMoveBroadcastsUpdatedStateWhenMoveIsLegal() {
        GameSession session = newSession();
        when(sessionRegistry.find(session.gameId())).thenReturn(Optional.of(session));

        controller.handleMove(session.gameId(), new MoveMessage(session.gameId(), "e2", "e4", null));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(GameStateSyncMessage.class);

        GameStateSyncMessage stateSync = (GameStateSyncMessage) payload.getValue();
        assertThat(stateSync.turn()).isEqualTo("black"); // el turno ya cambió tras la jugada
        assertThat(stateSync.boardFen()).contains("4P3"); // el peón blanco ya está en e4
    }

    @Test
    void handleMoveSendsErrorWhenMoveIsIllegal() {
        GameSession session = newSession();
        when(sessionRegistry.find(session.gameId())).thenReturn(Optional.of(session));

        // e2-e5 no es un movimiento legal de peón (no puede saltar 3 casillas de golpe)
        controller.handleMove(session.gameId(), new MoveMessage(session.gameId(), "e2", "e5", null));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(ErrorMessage.class);
        assertThat(((ErrorMessage) payload.getValue()).code()).isEqualTo("ILLEGAL_MOVE");
    }

    @Test
    void handleJoinSendsCurrentStateWhenGameExists() {
        GameSession session = newSession();
        when(sessionRegistry.find(session.gameId())).thenReturn(Optional.of(session));

        controller.handleJoin(session.gameId());

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(GameStateSyncMessage.class);
    }

    @Test
    void handleJoinSendsErrorWhenGameDoesNotExist() {
        when(sessionRegistry.find("missing-game")).thenReturn(Optional.empty());

        controller.handleJoin("missing-game");

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/missing-game"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(ErrorMessage.class);
    }
}
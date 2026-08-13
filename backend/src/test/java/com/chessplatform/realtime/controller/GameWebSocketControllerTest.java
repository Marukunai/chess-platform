package com.chessplatform.realtime.controller;

import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
import com.chessplatform.realtime.dto.ErrorMessage;
import com.chessplatform.realtime.dto.GameOverMessage;
import com.chessplatform.realtime.dto.GameStateSyncMessage;
import com.chessplatform.realtime.dto.MoveMessage;
import com.chessplatform.realtime.dto.ResignMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests con Mockito, sin contexto de Spring — no hace falta levantar un servidor
 * WebSocket real para comprobar que el controlador toma las decisiones correctas
 * (validar identidad, validar la jugada, aplicar, difundir), solo verificar cómo llama a
 * sus dos colaboradores. El Principal se simula directamente (UsernamePasswordAuthenticationToken)
 * en vez de pasar por StompAuthChannelInterceptor de verdad — ese ya tiene su propio test.
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

    private static Principal principalFor(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void handleMoveSendsErrorWhenGameDoesNotExist() {
        when(sessionRegistry.find("missing-game")).thenReturn(Optional.empty());

        controller.handleMove("missing-game", new MoveMessage("missing-game", "e2", "e4", null),
                principalFor("white-player"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/missing-game"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(ErrorMessage.class);
        assertThat(((ErrorMessage) payload.getValue()).code()).isEqualTo("GAME_NOT_FOUND");
    }

    @Test
    void handleMoveBroadcastsUpdatedStateWhenMoveIsLegalAndSenderIsThePlayerToMove() {
        GameSession session = newSession();
        when(sessionRegistry.find(session.gameId())).thenReturn(Optional.of(session));

        controller.handleMove(session.gameId(), new MoveMessage(session.gameId(), "e2", "e4", null),
                principalFor("white-player")); // le toca a blancas, y el Principal es white-player

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
        controller.handleMove(session.gameId(), new MoveMessage(session.gameId(), "e2", "e5", null),
                principalFor("white-player"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(ErrorMessage.class);
        assertThat(((ErrorMessage) payload.getValue()).code()).isEqualTo("ILLEGAL_MOVE");
    }

    @Test
    void handleMoveRejectsMoveFromThePlayerWhoseTurnItIsNot() {
        GameSession session = newSession(); // le toca a blancas
        when(sessionRegistry.find(session.gameId())).thenReturn(Optional.of(session));

        // black-player intenta mover una jugada de blancas
        controller.handleMove(session.gameId(), new MoveMessage(session.gameId(), "e2", "e4", null),
                principalFor("black-player"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(ErrorMessage.class);
        assertThat(((ErrorMessage) payload.getValue()).code()).isEqualTo("NOT_YOUR_TURN");
    }

    @Test
    void handleMoveRejectsMoveFromSomeoneNotInTheGameAtAll() {
        GameSession session = newSession();
        when(sessionRegistry.find(session.gameId())).thenReturn(Optional.of(session));

        controller.handleMove(session.gameId(), new MoveMessage(session.gameId(), "e2", "e4", null),
                principalFor("un-espectador-cualquiera"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        assertThat(((ErrorMessage) payload.getValue()).code()).isEqualTo("NOT_YOUR_TURN");
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

    @Test
    void handleResignEndsTheGameInFavorOfTheOpponentWhenWhiteResigns() {
        GameSession session = newSession();
        when(sessionRegistry.find(session.gameId())).thenReturn(Optional.of(session));

        controller.handleResign(session.gameId(), new ResignMessage(session.gameId()),
                principalFor("white-player"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(GameOverMessage.class);
        GameOverMessage gameOver = (GameOverMessage) payload.getValue();
        assertThat(gameOver.result()).isEqualTo("0-1"); // ganan negras
        assertThat(gameOver.reason()).isEqualTo("resignation");
        verify(sessionRegistry).remove(session.gameId());
    }

    @Test
    void handleResignRejectsSomeoneNotInTheGame() {
        GameSession session = newSession();
        when(sessionRegistry.find(session.gameId())).thenReturn(Optional.of(session));

        controller.handleResign(session.gameId(), new ResignMessage(session.gameId()),
                principalFor("un-espectador-cualquiera"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        assertThat(((ErrorMessage) payload.getValue()).code()).isEqualTo("FORBIDDEN");
        verify(sessionRegistry, never()).remove(anyString()); // no debería tocar el registro para eliminar la partida
    }
}
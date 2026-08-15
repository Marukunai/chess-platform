package com.chessplatform.realtime.controller;

import com.chessplatform.engine.Move;
import com.chessplatform.engine.Square;
import com.chessplatform.realtime.GameEndNotifier;
import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
import com.chessplatform.realtime.dto.ErrorMessage;
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
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests con Mockito, sin contexto de Spring — no hace falta levantar un servidor
 * WebSocket real para comprobar que el controlador toma las decisiones correctas
 * (validar identidad, validar la jugada, aplicar, difundir, terminar la partida), solo
 * verificar cómo llama a sus colaboradores. El Principal se simula directamente
 * (UsernamePasswordAuthenticationToken) en vez de pasar por StompAuthChannelInterceptor
 * de verdad — ese ya tiene su propio test.
 */
@ExtendWith(MockitoExtension.class)
class GameWebSocketControllerTest {

    @Mock
    private GameSessionRegistry sessionRegistry;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private GameEndNotifier gameEndNotifier;

    private GameWebSocketController controller;

    @BeforeEach
    void setUp() {
        controller = new GameWebSocketController(sessionRegistry, messagingTemplate, gameEndNotifier);
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
        assertThat(stateSync.movesNotation()).containsExactly("e4");
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
    void handleMoveEndsTheGameOnCheckmate() {
        // Mate del necio: la partida legal más corta que termina en jaque mate.
        // 1. f3 e5  2. g4 Qh4#
        GameSession session = newSession();
        when(sessionRegistry.find(session.gameId())).thenReturn(Optional.of(session));

        session.applyMove(new Move(Square.of(5, 1), Square.of(5, 2))); // 1. f3
        session.applyMove(new Move(Square.of(4, 6), Square.of(4, 4))); // 1... e5
        session.applyMove(new Move(Square.of(6, 1), Square.of(6, 3))); // 2. g4

        // 2... Qh4#, enviada por black-player a través del controlador de verdad
        controller.handleMove(session.gameId(), new MoveMessage(session.gameId(), "d8", "h4", null),
                principalFor("black-player"));

        verify(gameEndNotifier).endGame(session, "0-1", "checkmate"); // ganan negras
        verify(messagingTemplate, never()).convertAndSend(anyString(), org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void handleMoveEndsTheGameAsADrawWhenFiftyMoveRuleIsReached() {
        GameSession session = newSession();
        when(sessionRegistry.find(session.gameId())).thenReturn(Optional.of(session));

        Move whiteOut = new Move(Square.of(1, 0), Square.of(2, 2)); // Nb1-c3
        Move blackOut = new Move(Square.of(1, 7), Square.of(2, 5)); // Nb8-c6
        Move whiteBack = new Move(Square.of(2, 2), Square.of(1, 0)); // Nc3-b1
        Move blackBack = new Move(Square.of(2, 5), Square.of(1, 7)); // Nc6-b8

        // 96 semijugadas = 24 vaivenes completos, sin peón ni captura, aplicadas
        // directamente sobre la sesión (sin pasar por el controlador — solo montamos el
        // escenario). Más 3 sueltas para dejar el contador en 99 justo antes de la
        // jugada que sí manda el test a través del controlador.
        for (int i = 0; i < 24; i++) {
            session.applyMove(whiteOut);
            session.applyMove(blackOut);
            session.applyMove(whiteBack);
            session.applyMove(blackBack);
        }
        session.applyMove(whiteOut);
        session.applyMove(blackOut);
        session.applyMove(whiteBack);

        // Jugada 100, la manda de verdad negras (Nc6-b8) a través del controlador.
        controller.handleMove(session.gameId(), new MoveMessage(session.gameId(), "c6", "b8", null),
                principalFor("black-player"));

        verify(gameEndNotifier).endGame(session, "1/2-1/2", "fifty-move-rule");
    }

    @Test
    void handleMoveEndsTheGameAsADrawByThreefoldRepetition() {
        GameSession session = newSession();
        when(sessionRegistry.find(session.gameId())).thenReturn(Optional.of(session));

        Move whiteOut = new Move(Square.of(1, 0), Square.of(2, 2)); // Nb1-c3
        Move blackOut = new Move(Square.of(1, 7), Square.of(2, 5)); // Nb8-c6
        Move whiteBack = new Move(Square.of(2, 2), Square.of(1, 0)); // Nc3-b1
        Move blackBack = new Move(Square.of(2, 5), Square.of(1, 7)); // Nc6-b8

        // Primer vaivén completo: 2ª aparición de la posición inicial.
        session.applyMove(whiteOut);
        session.applyMove(blackOut);
        session.applyMove(whiteBack);
        session.applyMove(blackBack);

        // Segundo vaivén, salvo la última jugada — esa la manda el controlador de verdad.
        session.applyMove(whiteOut);
        session.applyMove(blackOut);
        session.applyMove(whiteBack);

        controller.handleMove(session.gameId(), new MoveMessage(session.gameId(), "c6", "b8", null),
                principalFor("black-player"));

        verify(gameEndNotifier).endGame(session, "1/2-1/2", "threefold-repetition");
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

        verify(gameEndNotifier).endGame(session, "0-1", "resignation"); // ganan negras
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
        verify(gameEndNotifier, never()).endGame(org.mockito.ArgumentMatchers.any(), anyString(), anyString());
    }

    @Test
    void concurrentMovesForTheSameGameDoNotBothGetApplied() throws InterruptedException {
        // Regresión directa del hallazgo de condición de carrera: dos hilos mandando la
        // MISMA jugada casi a la vez para la misma partida. Sin sincronizar sobre la
        // GameSession, los dos podrían pasar la comprobación de legalidad antes de que
        // cualquiera mute el tablero, y aplicarse las dos. Con el bloqueo, el segundo ve
        // el tablero ya actualizado por el primero (turno ya cambiado a negras), así que
        // se rechaza — solo una jugada debería quedar en el historial.
        //
        // Es un test con hilos reales, así que depende algo del scheduling del SO — pero
        // arrancar ambos con un CountDownLatch maximiza las probabilidades de solape, y
        // sessionRegistry/messagingTemplate/gameEndNotifier son mocks de Mockito, seguros
        // de invocar desde varios hilos sin más.
        GameSession session = newSession(); // le toca a blancas
        when(sessionRegistry.find(session.gameId())).thenReturn(Optional.of(session));

        MoveMessage move = new MoveMessage(session.gameId(), "e2", "e4", null);
        CountDownLatch startLatch = new CountDownLatch(1);

        Runnable attempt = () -> {
            try {
                startLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            controller.handleMove(session.gameId(), move, principalFor("white-player"));
        };

        Thread first = new Thread(attempt);
        Thread second = new Thread(attempt);
        first.start();
        second.start();
        startLatch.countDown(); // suelta a los dos casi a la vez
        first.join();
        second.join();

        assertThat(session.board().moveHistory()).hasSize(1);
    }
}
package com.chessplatform.realtime;

import com.chessplatform.engine.Move;
import com.chessplatform.engine.Square;
import com.chessplatform.realtime.dto.GameStateSyncMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Extraído de GameWebSocketController — mismos escenarios que ya cubrían sus tests
 * antes de la extracción (jaque mate, tablas por 50 movimientos, por repetición), pero
 * comprobados aquí directamente en vez de a través del controlador, ya que ahora es
 * esta clase la que de verdad decide y aplica esa lógica.
 */
@ExtendWith(MockitoExtension.class)
class GameStateBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private GameEndNotifier gameEndNotifier;

    private GameStateBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new GameStateBroadcaster(messagingTemplate, gameEndNotifier);
    }

    private static GameSession newSession() {
        return new GameSession("white-player", "black-player", Duration.ofMinutes(10), Duration.ZERO);
    }

    @Test
    void broadcastAndCheckEndAlwaysSendsTheCurrentState() {
        GameSession session = newSession();
        session.applyMove(new Move(Square.of(4, 1), Square.of(4, 3))); // e2-e4

        broadcaster.broadcastAndCheckEnd(session);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(GameStateSyncMessage.class);
        assertThat(((GameStateSyncMessage) payload.getValue()).turn()).isEqualTo("black");
    }

    @Test
    void broadcastAndCheckEndDoesNotEndAnOngoingGame() {
        GameSession session = newSession();
        session.applyMove(new Move(Square.of(4, 1), Square.of(4, 3))); // e2-e4

        broadcaster.broadcastAndCheckEnd(session);

        verify(gameEndNotifier, never()).endGame(any(), any(), any());
    }

    @Test
    void broadcastAndCheckEndEndsTheGameOnCheckmate() {
        // Mate del necio: 1. f3 e5 2. g4 Qh4#
        GameSession session = newSession();
        session.applyMove(new Move(Square.of(5, 1), Square.of(5, 2))); // 1. f3
        session.applyMove(new Move(Square.of(4, 6), Square.of(4, 4))); // 1... e5
        session.applyMove(new Move(Square.of(6, 1), Square.of(6, 3))); // 2. g4
        session.applyMove(new Move(Square.of(3, 7), Square.of(7, 3))); // 2... Qh4#

        broadcaster.broadcastAndCheckEnd(session);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        assertThat(((GameStateSyncMessage) payload.getValue()).movesNotation()).endsWith("Qh4#");
        verify(gameEndNotifier).endGame(session, "0-1", "checkmate");
    }

    @Test
    void broadcastAndCheckEndEndsTheGameAsADrawWhenFiftyMoveRuleIsReached() {
        GameSession session = newSession();
        Move whiteOut = new Move(Square.of(1, 0), Square.of(2, 2)); // Nb1-c3
        Move blackOut = new Move(Square.of(1, 7), Square.of(2, 5)); // Nb8-c6
        Move whiteBack = new Move(Square.of(2, 2), Square.of(1, 0)); // Nc3-b1
        Move blackBack = new Move(Square.of(2, 5), Square.of(1, 7)); // Nc6-b8

        for (int i = 0; i < 24; i++) {
            session.applyMove(whiteOut);
            session.applyMove(blackOut);
            session.applyMove(whiteBack);
            session.applyMove(blackBack);
        }
        session.applyMove(whiteOut);
        session.applyMove(blackOut);
        session.applyMove(whiteBack);
        session.applyMove(blackBack); // jugada 100 — 50 movimientos completos sin peón ni captura

        broadcaster.broadcastAndCheckEnd(session);

        verify(gameEndNotifier).endGame(session, "1/2-1/2", "fifty-move-rule");
    }

    @Test
    void broadcastAndCheckEndEndsTheGameAsADrawByThreefoldRepetition() {
        GameSession session = newSession();
        Move whiteOut = new Move(Square.of(1, 0), Square.of(2, 2)); // Nb1-c3
        Move blackOut = new Move(Square.of(1, 7), Square.of(2, 5)); // Nb8-c6
        Move whiteBack = new Move(Square.of(2, 2), Square.of(1, 0)); // Nc3-b1
        Move blackBack = new Move(Square.of(2, 5), Square.of(1, 7)); // Nc6-b8

        session.applyMove(whiteOut);
        session.applyMove(blackOut);
        session.applyMove(whiteBack);
        session.applyMove(blackBack); // 2ª aparición de la posición inicial

        session.applyMove(whiteOut);
        session.applyMove(blackOut);
        session.applyMove(whiteBack);
        session.applyMove(blackBack); // 3ª aparición

        broadcaster.broadcastAndCheckEnd(session);

        verify(gameEndNotifier).endGame(session, "1/2-1/2", "threefold-repetition");
    }

    @Test
    void broadcastAndCheckEndAnnotatesTheLastMoveWithCheckSymbolWhenNotMate() {
        GameSession session = newSession();
        // Jaque simple (no mate) — comprobación del "+" en vez del "#"
        session.applyMove(new Move(Square.of(4, 1), Square.of(4, 3))); // 1. e4
        session.applyMove(new Move(Square.of(3, 6), Square.of(3, 4))); // 1... d5
        session.applyMove(new Move(Square.of(5, 0), Square.of(1, 4))); // 2. Bb5+

        broadcaster.broadcastAndCheckEnd(session);

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/game/" + session.gameId()), payload.capture());
        assertThat(((GameStateSyncMessage) payload.getValue()).movesNotation()).anyMatch(move -> move.endsWith("+"));
        verify(gameEndNotifier, never()).endGame(any(), any(), any());
    }
}
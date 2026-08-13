package com.chessplatform.realtime;

import com.chessplatform.realtime.dto.GameOverMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GameEndNotifierTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private GameSessionRegistry sessionRegistry;
    private GameEndNotifier notifier;

    @BeforeEach
    void setUp() {
        sessionRegistry = new GameSessionRegistry();
        notifier = new GameEndNotifier(sessionRegistry, messagingTemplate);
    }

    @Test
    void endGameBroadcastsGameOverAndRemovesTheSessionFromTheRegistry() {
        GameSession session = new GameSession("white-player", "black-player", Duration.ofMinutes(10), Duration.ZERO);
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
}
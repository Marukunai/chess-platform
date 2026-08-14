package com.chessplatform.realtime;

import com.chessplatform.engine.Color;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerConnectionListenerTest {

    private final GameSessionRegistry sessionRegistry = new GameSessionRegistry();
    private final PlayerConnectionListener listener = new PlayerConnectionListener(sessionRegistry);

    private static Principal principalFor(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    private static GameSession newSession() {
        return new GameSession("white-player", "black-player", Duration.ofMinutes(10), Duration.ZERO);
    }

    @Test
    void handlePrincipalMarksThePlayerDisconnectedInTheirActiveGame() {
        GameSession session = newSession();
        sessionRegistry.create(session);

        listener.handlePrincipal(principalFor("white-player"), false);

        assertThat(session.hasExceededDisconnectGracePeriod(Color.WHITE, Duration.ZERO)).isTrue();
    }

    @Test
    void handlePrincipalMarksThePlayerConnectedAgain() {
        GameSession session = newSession();
        sessionRegistry.create(session);

        listener.handlePrincipal(principalFor("white-player"), false);
        listener.handlePrincipal(principalFor("white-player"), true);

        assertThat(session.hasExceededDisconnectGracePeriod(Color.WHITE, Duration.ZERO)).isFalse();
    }

    @Test
    void handlePrincipalDoesNothingWhenPrincipalIsNull() {
        GameSession session = newSession();
        sessionRegistry.create(session);

        listener.handlePrincipal(null, false); // no debe lanzar excepción

        assertThat(session.hasExceededDisconnectGracePeriod(Color.WHITE, Duration.ZERO)).isFalse();
    }

    @Test
    void handlePrincipalDoesNothingWhenThePlayerHasNoActiveGame() {
        // No debe lanzar excepción aunque no haya ninguna partida en el registro.
        listener.handlePrincipal(principalFor("nadie-jugando"), false);
    }
}
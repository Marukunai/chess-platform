package com.chessplatform.realtime;

import com.chessplatform.engine.Color;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Escucha los eventos que Spring publica al conectar/desconectar una sesión WebSocket, y
 * marca al jugador correspondiente como conectado/desconectado en su partida activa (si
 * tiene una) — ver GameSession.markConnected()/markDisconnected() y
 * GameAbandonmentService, que es quien realmente decide cuándo declarar abandono tras la
 * ventana de gracia.
 */
@Component
public class PlayerConnectionListener {

    private final GameSessionRegistry sessionRegistry;

    public PlayerConnectionListener(GameSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        handlePrincipal(StompHeaderAccessor.wrap(event.getMessage()).getUser(), false);
    }

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        handlePrincipal(StompHeaderAccessor.wrap(event.getMessage()).getUser(), true);
    }

    /**
     * Separado de los @EventListener a propósito: así se puede testear pasando un
     * Principal normal, sin tener que construir SessionConnectedEvent/
     * SessionDisconnectEvent (eventos internos de Spring) en los tests.
     */
    void handlePrincipal(Principal principal, boolean connected) {
        if (principal == null) {
            return;
        }
        sessionRegistry.findByPlayerId(principal.getName()).ifPresent(session -> {
            synchronized (session) {
                Color color = session.colorOf(principal.getName());
                if (connected) {
                    session.markConnected(color);
                } else {
                    session.markDisconnected(color);
                }
            }
        });
    }
}
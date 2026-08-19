package com.chessplatform.presence;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Igual que PlayerConnectionListener (mismos eventos de Spring), pero para presencia en
 * general en vez de para partidas concretas — separado en su propia clase a propósito,
 * son dos responsabilidades distintas aunque escuchen los mismos eventos.
 */
@Component
public class PresenceConnectionListener {

    private final PresenceService presenceService;

    public PresenceConnectionListener(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        handlePrincipal(StompHeaderAccessor.wrap(event.getMessage()).getUser(), true);
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        handlePrincipal(StompHeaderAccessor.wrap(event.getMessage()).getUser(), false);
    }

    /** Separado de los @EventListener a propósito, mismo motivo que en PlayerConnectionListener: testeable con un Principal normal. */
    void handlePrincipal(Principal principal, boolean connected) {
        if (principal == null) {
            return;
        }
        if (connected) {
            presenceService.markOnlineAndNotifyFriends(principal.getName());
        } else {
            presenceService.markOfflineAndNotifyFriends(principal.getName());
        }
    }
}
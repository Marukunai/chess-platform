package com.chessplatform.presence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PresenceConnectionListenerTest {

    @Mock
    private PresenceService presenceService;

    private PresenceConnectionListener listener;

    @BeforeEach
    void setUp() {
        listener = new PresenceConnectionListener(presenceService);
    }

    private static Authentication principalFor(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void handlePrincipalConnectedMarksOnline() {
        listener.handlePrincipal(principalFor("alice-id"), true);

        verify(presenceService).markOnlineAndNotifyFriends("alice-id");
        verify(presenceService, never()).markOfflineAndNotifyFriends(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handlePrincipalDisconnectedMarksOffline() {
        listener.handlePrincipal(principalFor("alice-id"), false);

        verify(presenceService).markOfflineAndNotifyFriends("alice-id");
        verify(presenceService, never()).markOnlineAndNotifyFriends(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void handlePrincipalDoesNothingWhenThereIsNoPrincipal() {
        listener.handlePrincipal(null, true);

        verify(presenceService, never()).markOnlineAndNotifyFriends(org.mockito.ArgumentMatchers.anyString());
        verify(presenceService, never()).markOfflineAndNotifyFriends(org.mockito.ArgumentMatchers.anyString());
    }
}

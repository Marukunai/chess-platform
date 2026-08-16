package com.chessplatform.matchmaking.controller;

import com.chessplatform.matchmaking.MatchmakingQueue;
import com.chessplatform.matchmaking.TimeControl;
import com.chessplatform.matchmaking.dto.MatchmakingJoinMessage;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.UserRepository;
import com.chessplatform.realtime.dto.ErrorMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchmakingControllerTest {

    @Mock
    private MatchmakingQueue queue;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private MatchmakingController controller;

    @BeforeEach
    void setUp() {
        controller = new MatchmakingController(queue, userRepository, messagingTemplate);
    }

    private static Principal principalFor(String userId) {
        return new UsernamePasswordAuthenticationToken(userId, null, List.of());
    }

    @Test
    void joinEnqueuesPlayerWithRatingFromRepository() {
        User user = new User("maru", "hash"); // rating por defecto: 1500.0
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        controller.join(new MatchmakingJoinMessage("BLITZ"), principalFor("user-1"));

        verify(queue).enqueue("user-1", "maru", 1500, TimeControl.BLITZ);
    }

    @Test
    void joinUsesDefaultRatingWhenUserIsNotFound() {
        when(userRepository.findById("user-1")).thenReturn(Optional.empty());

        controller.join(new MatchmakingJoinMessage("RAPID"), principalFor("user-1"));

        // Sin usuario en la base de datos, el nombre de usuario también cae al id del
        // principal — igual que ya hacía el rating con su valor Glicko-2 por defecto.
        verify(queue).enqueue("user-1", "user-1", 1500, TimeControl.RAPID);
    }

    @Test
    void joinDoesNothingWhenThereIsNoPrincipal() {
        controller.join(new MatchmakingJoinMessage("BLITZ"), null);

        verify(queue, never()).enqueue(anyString(), anyString(), anyInt(), any());
    }

    @Test
    void joinSendsErrorForAnUnknownTimeControl() {
        controller.join(new MatchmakingJoinMessage("ULTRA-BULLET-INVENTADO"), principalFor("user-1"));

        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/matchmaking/user-1"), payload.capture());
        assertThat(payload.getValue()).isInstanceOf(ErrorMessage.class);
    }

    @Test
    void leaveRemovesThePlayerFromTheQueue() {
        controller.leave(principalFor("user-1"));

        verify(queue).remove("user-1");
    }

    @Test
    void leaveDoesNothingWhenThereIsNoPrincipal() {
        controller.leave(null);

        verify(queue, never()).remove(anyString());
    }
}
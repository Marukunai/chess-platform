package com.chessplatform.realtime.config;

import com.chessplatform.auth.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StompAuthChannelInterceptorTest {

    private static final String SECRET = "test-secret-key-at-least-32-characters-long";

    private final JwtService jwtService = new JwtService(SECRET, 60_000);
    private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(jwtService);

    private Message<byte[]> connectMessageWithHeader(String authorizationHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        // Igual que hace Spring de verdad antes de pasar el CONNECT por los
        // interceptores: sin esto, getMessageHeaders() sella las cabeceras como
        // inmutables al construirlas, y accessor.setUser() (dentro del interceptor)
        // petaría con "Already immutable" al intentar mutarlas después.
        accessor.setLeaveMutable(true);
        if (authorizationHeader != null) {
            accessor.setNativeHeader("Authorization", authorizationHeader);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void preSendSetsThePrincipalWhenTokenIsValid() {
        String token = jwtService.generateToken("user-123");
        Message<byte[]> connectMessage = connectMessageWithHeader("Bearer " + token);

        Message<?> result = interceptor.preSend(connectMessage, null);

        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        assertThat(resultAccessor.getUser()).isNotNull();
        assertThat(resultAccessor.getUser().getName()).isEqualTo("user-123");
    }

    @Test
    void preSendRejectsConnectWithoutAuthorizationHeader() {
        Message<byte[]> connectMessage = connectMessageWithHeader(null);

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void preSendRejectsConnectWithMalformedToken() {
        Message<byte[]> connectMessage = connectMessageWithHeader("Bearer esto-no-es-un-jwt");

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void preSendRejectsHeaderWithoutBearerPrefix() {
        String token = jwtService.generateToken("user-123");
        Message<byte[]> connectMessage = connectMessageWithHeader(token); // sin "Bearer " delante

        assertThatThrownBy(() -> interceptor.preSend(connectMessage, null))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void preSendLeavesNonConnectFramesUntouched() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        Message<byte[]> sendMessage = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(sendMessage, null);

        assertThat(result).isSameAs(sendMessage);
    }
}
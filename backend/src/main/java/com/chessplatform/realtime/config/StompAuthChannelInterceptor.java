package com.chessplatform.realtime.config;

import com.chessplatform.auth.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Valida el JWT en el frame STOMP CONNECT y fija el Principal de la sesión WebSocket a
 * partir de él.
 *
 * Por qué aquí y no en el handshake HTTP inicial: los navegadores no permiten poner
 * cabeceras arbitrarias (como Authorization) en la petición de upgrade de WebSocket, pero
 * el frame STOMP CONNECT sí soporta cabeceras propias una vez la conexión ya está
 * abierta — es el punto estándar para autenticar sobre STOMP.
 *
 * Una vez fijado aquí, el Principal queda disponible automáticamente en los
 * @MessageMapping del resto de la sesión (ver GameWebSocketController, que lo recibe
 * como parámetro sin tener que revalidar el token en cada mensaje).
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;

    public StompAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (token == null) {
                throw new BadCredentialsException("Falta el token de autenticación en el CONNECT");
            }

            String userId;
            try {
                userId = jwtService.extractUserId(token);
            } catch (RuntimeException e) {
                throw new BadCredentialsException("Token de autenticación inválido o caducado", e);
            }

            Authentication authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
            accessor.setUser(authentication);
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length());
    }
}
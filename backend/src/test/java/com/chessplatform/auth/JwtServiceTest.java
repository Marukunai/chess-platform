package com.chessplatform.auth;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    // HMAC-SHA necesita una clave de al menos 256 bits (32 caracteres) — más corta que
    // esto, Keys.hmacShaKeyFor() lanza una excepción al construir el servicio.
    private static final String TEST_SECRET = "test-secret-key-at-least-32-characters-long";

    private JwtService newService(long expirationMs) {
        return new JwtService(TEST_SECRET, expirationMs);
    }

    @Test
    void generateTokenAndExtractUserIdRoundTrip() {
        JwtService jwtService = newService(60_000);

        String token = jwtService.generateToken("user-123");

        assertThat(jwtService.extractUserId(token)).isEqualTo("user-123");
    }

    @Test
    void extractUserIdThrowsForAMalformedToken() {
        JwtService jwtService = newService(60_000);

        assertThatThrownBy(() -> jwtService.extractUserId("esto-no-es-un-jwt"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractUserIdThrowsForATokenExpiredInThePast() {
        // Expiración negativa: el token ya nace caducado, sin depender de Thread.sleep().
        JwtService jwtService = newService(-1);

        String expiredToken = jwtService.generateToken("user-123");

        assertThatThrownBy(() -> jwtService.extractUserId(expiredToken))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void extractUserIdThrowsWhenSignedWithADifferentSecret() {
        JwtService signer = newService(60_000);
        JwtService verifier = new JwtService("otro-secreto-completamente-distinto-32-chars", 60_000);

        String token = signer.generateToken("user-123");

        assertThatThrownBy(() -> verifier.extractUserId(token))
                .isInstanceOf(JwtException.class);
    }
}
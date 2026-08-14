package com.chessplatform.auth.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CorsPropertiesTest {

    @Test
    void parsesASingleOrigin() {
        CorsProperties properties = new CorsProperties("http://localhost:5500");

        assertThat(properties.allowedOrigins()).containsExactly("http://localhost:5500");
    }

    @Test
    void parsesMultipleCommaSeparatedOriginsAndTrimsWhitespace() {
        CorsProperties properties = new CorsProperties(
                "http://localhost:5500, https://chess-platform-web.onrender.com ,https://otro.com");

        assertThat(properties.allowedOrigins()).containsExactly(
                "http://localhost:5500",
                "https://chess-platform-web.onrender.com",
                "https://otro.com"
        );
    }

    @Test
    void ignoresEmptyEntriesFromTrailingCommas() {
        CorsProperties properties = new CorsProperties("http://localhost:5500,,");

        assertThat(properties.allowedOrigins()).containsExactly("http://localhost:5500");
    }
}
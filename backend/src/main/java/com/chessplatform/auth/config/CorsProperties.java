package com.chessplatform.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Orígenes permitidos para CORS (HTTP) y para el endpoint WebSocket — un único sitio que
 * parsea app.cors.allowed-origins, para que SecurityConfig y WebSocketConfig no dupliquen
 * la misma lógica de parseo.
 */
@Component
public class CorsProperties {

    private final List<String> allowedOrigins;

    public CorsProperties(@Value("${app.cors.allowed-origins}") String allowedOriginsProperty) {
        this.allowedOrigins = Arrays.stream(allowedOriginsProperty.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
    }

    public List<String> allowedOrigins() {
        return allowedOrigins;
    }
}
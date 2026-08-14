package com.chessplatform;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de salud para el healthcheck del proveedor de hosting (Render) — sin esto, no
 * tiene una forma fiable de saber si el backend está vivo tras desplegarlo. Público a
 * propósito (ver SecurityConfig): nadie le manda un JWT a un healthcheck.
 */
@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "OK";
    }
}
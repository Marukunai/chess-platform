package com.chessplatform.auth.controller;

import com.chessplatform.auth.JwtService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    public record RegisterRequest(String username, String password) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record AuthResponse(String token) {
    }

    @PostMapping("/register")
    public AuthResponse register(@RequestBody RegisterRequest request) {
        // TODO (Fase 1): validar username disponible, hashear password
        // (BCryptPasswordEncoder), persistir usuario, devolver token.
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        // TODO (Fase 1): validar credenciales contra la base de datos, generar token si
        // son correctas.
        throw new UnsupportedOperationException("Pendiente de implementar");
    }
}

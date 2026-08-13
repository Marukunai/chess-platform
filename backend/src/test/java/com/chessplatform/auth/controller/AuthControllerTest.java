package com.chessplatform.auth.controller;

import com.chessplatform.auth.JwtService;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(userRepository, passwordEncoder, jwtService);
    }

    @Test
    void registerCreatesUserWithHashedPasswordAndReturnsToken() {
        when(userRepository.findByUsername("maru")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("contraseña-segura")).thenReturn("hash-simulado");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateToken(any())).thenReturn("token-simulado");

        AuthController.AuthResponse response = controller.register(
                new AuthController.RegisterRequest("maru", "contraseña-segura"));

        assertThat(response.token()).isEqualTo("token-simulado");
    }

    @Test
    void registerRejectsUsernameAlreadyTaken() {
        when(userRepository.findByUsername("maru"))
                .thenReturn(Optional.of(new User("maru", "hash-existente")));

        assertThatThrownBy(() -> controller.register(
                new AuthController.RegisterRequest("maru", "contraseña-segura")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void registerRejectsPasswordTooShort() {
        assertThatThrownBy(() -> controller.register(
                new AuthController.RegisterRequest("maru", "corta")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void loginReturnsTokenWhenCredentialsAreCorrect() {
        User user = new User("maru", "hash-almacenado");
        when(userRepository.findByUsername("maru")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("contraseña-correcta", "hash-almacenado")).thenReturn(true);
        when(jwtService.generateToken(any())).thenReturn("token-simulado");

        AuthController.AuthResponse response = controller.login(
                new AuthController.LoginRequest("maru", "contraseña-correcta"));

        assertThat(response.token()).isEqualTo("token-simulado");
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = new User("maru", "hash-almacenado");
        when(userRepository.findByUsername("maru")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("contraseña-incorrecta", "hash-almacenado")).thenReturn(false);

        assertThatThrownBy(() -> controller.login(
                new AuthController.LoginRequest("maru", "contraseña-incorrecta")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginRejectsUnknownUsernameWithTheSameMessageAsWrongPassword() {
        // Mismo status/mensaje que una contraseña incorrecta — evita que alguien pueda
        // averiguar qué nombres de usuario existen probando al tuntún.
        when(userRepository.findByUsername("fantasma")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.login(
                new AuthController.LoginRequest("fantasma", "cualquier-cosa")))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
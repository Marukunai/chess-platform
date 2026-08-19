package com.chessplatform.persistence.entity;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    // Real, no mockeado — un algoritmo puro y rápido, y así se comprueba el
    // comportamiento real de matches(), no una suposición de lo que haría un mock.
    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void matchesAnyRecentPasswordIsTrueForTheCurrentPassword() {
        User user = new User("alice", encoder.encode("actual123"));

        assertThat(user.matchesAnyRecentPassword("actual123", encoder)).isTrue();
    }

    @Test
    void matchesAnyRecentPasswordIsFalseForAPasswordNeverUsed() {
        User user = new User("alice", encoder.encode("actual123"));

        assertThat(user.matchesAnyRecentPassword("nunca-usada", encoder)).isFalse();
    }

    @Test
    void changePasswordRejectsReusingTheImmediatelyPreviousPassword() {
        User user = new User("alice", encoder.encode("primera123"));

        user.changePassword(encoder.encode("segunda123"));

        // "primera123" ya no es la actual, pero sigue estando entre las últimas usadas.
        assertThat(user.matchesAnyRecentPassword("primera123", encoder)).isTrue();
    }

    @Test
    void changePasswordRemembersTheLastFivePasswordsInTotal() {
        User user = new User("alice", encoder.encode("contrasena0"));

        // Cuatro cambios más -> cinco contraseñas distintas en total (la inicial + 4).
        user.changePassword(encoder.encode("contrasena1"));
        user.changePassword(encoder.encode("contrasena2"));
        user.changePassword(encoder.encode("contrasena3"));
        user.changePassword(encoder.encode("contrasena4"));

        for (int i = 0; i <= 4; i++) {
            assertThat(user.matchesAnyRecentPassword("contrasena" + i, encoder))
                    .as("contrasena%d debería seguir contando como usada recientemente", i)
                    .isTrue();
        }
    }

    @Test
    void changePasswordForgetsPasswordsOlderThanTheLastFive() {
        User user = new User("alice", encoder.encode("contrasena0"));

        // Cinco cambios más -> seis contraseñas en total, la primera (contrasena0) ya
        // debería haber caído fuera de las últimas 5.
        user.changePassword(encoder.encode("contrasena1"));
        user.changePassword(encoder.encode("contrasena2"));
        user.changePassword(encoder.encode("contrasena3"));
        user.changePassword(encoder.encode("contrasena4"));
        user.changePassword(encoder.encode("contrasena5"));

        assertThat(user.matchesAnyRecentPassword("contrasena0", encoder)).isFalse();
        // Pero las últimas 5 sí siguen contando: la actual (5) + las 4 anteriores (1-4).
        for (int i = 1; i <= 5; i++) {
            assertThat(user.matchesAnyRecentPassword("contrasena" + i, encoder)).isTrue();
        }
    }

    @Test
    void changePasswordActuallyUpdatesTheCurrentHash() {
        User user = new User("alice", encoder.encode("vieja123"));

        user.changePassword(encoder.encode("nueva456"));

        assertThat(encoder.matches("nueva456", user.getPasswordHash())).isTrue();
        assertThat(encoder.matches("vieja123", user.getPasswordHash())).isFalse();
    }
}
package com.chessplatform.presence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PresenceRegistryTest {

    private final PresenceRegistry registry = new PresenceRegistry();

    @Test
    void isOnlineIsFalseForSomeoneNeverMarkedOnline() {
        assertThat(registry.isOnline("alice-id")).isFalse();
    }

    @Test
    void markOnlineMakesIsOnlineTrue() {
        registry.markOnline("alice-id");

        assertThat(registry.isOnline("alice-id")).isTrue();
    }

    @Test
    void markOfflineMakesIsOnlineFalseAgain() {
        registry.markOnline("alice-id");

        registry.markOffline("alice-id");

        assertThat(registry.isOnline("alice-id")).isFalse();
    }

    @Test
    void newlyOnlineUsersAreNotDoNotDisturbByDefault() {
        registry.markOnline("alice-id");

        assertThat(registry.isDoNotDisturb("alice-id")).isFalse();
    }

    @Test
    void setDoNotDisturbTogglesTheFlagForAConnectedUser() {
        registry.markOnline("alice-id");

        registry.setDoNotDisturb("alice-id", true);

        assertThat(registry.isDoNotDisturb("alice-id")).isTrue();
    }

    @Test
    void setDoNotDisturbDoesNothingForSomeoneOffline() {
        registry.setDoNotDisturb("alice-id", true); // nunca se marcó online

        assertThat(registry.isDoNotDisturb("alice-id")).isFalse();
        assertThat(registry.isOnline("alice-id")).isFalse(); // tampoco la pone online de rebote
    }

    @Test
    void markOfflineClearsTheDoNotDisturbFlagToo() {
        registry.markOnline("alice-id");
        registry.setDoNotDisturb("alice-id", true);

        registry.markOffline("alice-id");
        registry.markOnline("alice-id"); // vuelve a conectarse

        assertThat(registry.isDoNotDisturb("alice-id")).isFalse(); // no arrastra el "no molestar" de la vez anterior
    }
}
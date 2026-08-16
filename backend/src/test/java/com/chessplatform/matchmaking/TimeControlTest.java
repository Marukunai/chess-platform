package com.chessplatform.matchmaking;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class TimeControlTest {

    @Test
    void presetNameForRecognizesEachOfTheFourPresets() {
        assertThat(TimeControl.presetNameFor(Duration.ofMinutes(1), Duration.ZERO)).contains("BULLET");
        assertThat(TimeControl.presetNameFor(Duration.ofMinutes(5), Duration.ofSeconds(3))).contains("BLITZ");
        assertThat(TimeControl.presetNameFor(Duration.ofMinutes(10), Duration.ofSeconds(5))).contains("RAPID");
        assertThat(TimeControl.presetNameFor(Duration.ofMinutes(30), Duration.ofSeconds(20))).contains("CLASSICAL");
    }

    @Test
    void presetNameForIsEmptyForDurationsThatMatchNoPreset() {
        assertThat(TimeControl.presetNameFor(Duration.ofMinutes(10), Duration.ZERO)).isEmpty();
    }
}
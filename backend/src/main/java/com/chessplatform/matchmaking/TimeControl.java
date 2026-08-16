package com.chessplatform.matchmaking;

import java.time.Duration;
import java.util.Optional;

/**
 * Presets de control de tiempo. El cliente elige uno al entrar en la cola de
 * matchmaking; solo se empareja a jugadores que pidieron el mismo (ver
 * MatchmakingService) — no tendría sentido emparejar a alguien buscando bullet con
 * alguien buscando partidas clásicas.
 */
public record TimeControl(Duration initialTime, Duration increment) {

    public static final TimeControl BULLET = new TimeControl(Duration.ofMinutes(1), Duration.ZERO);
    public static final TimeControl BLITZ = new TimeControl(Duration.ofMinutes(5), Duration.ofSeconds(3));
    public static final TimeControl RAPID = new TimeControl(Duration.ofMinutes(10), Duration.ofSeconds(5));
    public static final TimeControl CLASSICAL = new TimeControl(Duration.ofMinutes(30), Duration.ofSeconds(20));

    /**
     * El nombre del preset ("BULLET", "BLITZ"...) que tiene exactamente estas duraciones,
     * si alguno coincide. GameSession solo guarda las duraciones en bruto, no de qué
     * preset salieron (así no hizo falta tocar su constructor, usado en un montón de
     * tests que no necesitan saber nada de esto) — esto reconstruye el nombre cuando
     * hace falta, p. ej. para poder ofrecer "la misma modalidad" al proponer una
     * revancha (ver RematchService).
     */
    public static Optional<String> presetNameFor(Duration initialTime, Duration increment) {
        if (BULLET.initialTime().equals(initialTime) && BULLET.increment().equals(increment)) {
            return Optional.of("BULLET");
        }
        if (BLITZ.initialTime().equals(initialTime) && BLITZ.increment().equals(increment)) {
            return Optional.of("BLITZ");
        }
        if (RAPID.initialTime().equals(initialTime) && RAPID.increment().equals(increment)) {
            return Optional.of("RAPID");
        }
        if (CLASSICAL.initialTime().equals(initialTime) && CLASSICAL.increment().equals(increment)) {
            return Optional.of("CLASSICAL");
        }
        return Optional.empty();
    }
}
package com.chessplatform.matchmaking;

import java.time.Duration;

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
}
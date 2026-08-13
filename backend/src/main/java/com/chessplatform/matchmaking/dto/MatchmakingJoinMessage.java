package com.chessplatform.matchmaking.dto;

import com.chessplatform.matchmaking.TimeControl;

public record MatchmakingJoinMessage(String timeControl) {

    public TimeControl toTimeControl() {
        if (timeControl == null) {
            throw new IllegalArgumentException("Falta especificar el control de tiempo");
        }
        return switch (timeControl.toUpperCase()) {
            case "BULLET" -> TimeControl.BULLET;
            case "BLITZ" -> TimeControl.BLITZ;
            case "RAPID" -> TimeControl.RAPID;
            case "CLASSICAL" -> TimeControl.CLASSICAL;
            default -> throw new IllegalArgumentException("Control de tiempo desconocido: " + timeControl);
        };
    }
}
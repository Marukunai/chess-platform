package com.chessplatform.realtime;

import com.chessplatform.engine.Board;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Estado en memoria de una partida en curso. Vive en GameSessionRegistry mientras la
 * partida está activa; se persiste a PostgreSQL al finalizar.
 *
 * Reloj server-authoritative (ver ADR-004): no hay un hilo haciendo tick por partida. Se
 * guarda el tiempo restante de cada jugador junto al timestamp de la última jugada, y el
 * tiempo consumido se calcula bajo demanda (ver timeRemaining()).
 */
public class GameSession {

    private final String gameId;
    private final String whitePlayerId;
    private final String blackPlayerId;
    private final Board board;

    private Duration whiteTimeRemaining;
    private Duration blackTimeRemaining;
    private final Duration increment;
    private Instant lastMoveTimestamp;

    // TODO (Fase 1): estado de conexión por jugador + ventana de gracia para reconexión

    public GameSession(String whitePlayerId, String blackPlayerId, Duration initialTime, Duration increment) {
        this.gameId = UUID.randomUUID().toString();
        this.whitePlayerId = whitePlayerId;
        this.blackPlayerId = blackPlayerId;
        this.board = Board.initial();
        this.whiteTimeRemaining = initialTime;
        this.blackTimeRemaining = initialTime;
        this.increment = increment;
        this.lastMoveTimestamp = Instant.now();
    }

    public Duration timeRemaining(String playerId) {
        // TODO (Fase 1): restar el tiempo transcurrido desde lastMoveTimestamp si es el
        // turno de `playerId`, sin mutar estado (cálculo puro bajo demanda).
        throw new UnsupportedOperationException("Pendiente de implementar");
    }

    public String gameId() {
        return gameId;
    }

    public Board board() {
        return board;
    }

    public String whitePlayerId() {
        return whitePlayerId;
    }

    public String blackPlayerId() {
        return blackPlayerId;
    }
}

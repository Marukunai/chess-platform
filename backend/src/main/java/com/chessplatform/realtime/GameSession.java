package com.chessplatform.realtime;

import com.chessplatform.engine.Board;
import com.chessplatform.engine.Color;
import com.chessplatform.engine.Move;

import java.time.Clock;
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
 *
 * Concurrencia: esta clase NO es internamente thread-safe. Los mensajes STOMP de un
 * mismo cliente pueden procesarse en hilos distintos, y GameTimeoutService corre en su
 * propio hilo programado — así que dos operaciones sobre la MISMA partida (dos jugadas,
 * o una jugada y el barrido de timeout) podrían solaparse sin ninguna protección.
 * Cualquier código que lea o mute el estado de una GameSession (tablero, reloj) debe
 * hacerlo dentro de un bloque {@code synchronized (session)} — ver
 * GameWebSocketController y GameTimeoutService para los puntos ya cubiertos.
 * Sincronizar sobre la propia instancia (no sobre GameSessionRegistry ni un candado
 * global) mantiene el bloqueo acotado a una sola partida: dos partidas distintas nunca
 * se bloquean entre sí.
 */
public class GameSession {

    private final String gameId;
    private final String whitePlayerId;
    private final String blackPlayerId;
    private final Board board;
    private final Clock clock;
    private final Duration initialTime;

    private Duration whiteTimeRemaining;
    private Duration blackTimeRemaining;
    private final Duration increment;
    private Instant lastMoveTimestamp;

    // null = conectado (o nunca se desconectó); con timestamp = desde cuándo lleva
    // desconectado. Ver PlayerConnectionListener (quién los fija) y
    // GameAbandonmentService (quién los consulta).
    private Instant whiteDisconnectedAt;
    private Instant blackDisconnectedAt;

    public GameSession(String whitePlayerId, String blackPlayerId, Duration initialTime, Duration increment) {
        this(whitePlayerId, blackPlayerId, initialTime, increment, Clock.systemUTC());
    }

    /**
     * Constructor con Clock inyectable. Uso exclusivo para tests: así pueden controlar el
     * paso del tiempo con precisión (ver MutableClock en GameSessionTest) en vez de
     * depender de Thread.sleep() real, que es lento y propenso a inestabilidad.
     */
    GameSession(String whitePlayerId, String blackPlayerId, Duration initialTime, Duration increment, Clock clock) {
        this.gameId = UUID.randomUUID().toString();
        this.whitePlayerId = whitePlayerId;
        this.blackPlayerId = blackPlayerId;
        this.board = Board.initial();
        this.whiteTimeRemaining = initialTime;
        this.blackTimeRemaining = initialTime;
        this.initialTime = initialTime;
        this.increment = increment;
        this.clock = clock;
        this.lastMoveTimestamp = Instant.now(clock);
    }

    /**
     * Tiempo restante de `color`. Si es su turno ahora mismo, descuenta lo transcurrido
     * desde la última jugada; si no es su turno, su reloj no corre y se devuelve tal cual
     * está guardado. Cálculo puro bajo demanda, sin mutar estado — nunca negativo.
     */
    public Duration timeRemaining(Color color) {
        Duration stored = color == Color.WHITE ? whiteTimeRemaining : blackTimeRemaining;
        if (board.turn() != color) {
            return stored;
        }
        Duration elapsed = Duration.between(lastMoveTimestamp, Instant.now(clock));
        Duration remaining = stored.minus(elapsed);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public boolean isTimeout(Color color) {
        return timeRemaining(color).isZero();
    }

    /**
     * Aplica una jugada real de partida: consume el tiempo del jugador en turno (más el
     * incremento, si el time control lo tiene) y delega en Board.applyMove(). Este es el
     * único punto por el que debería pasar una jugada de partida — mantiene el reloj y el
     * tablero sincronizados en el mismo sitio, en vez de que cada llamador tenga que
     * acordarse de actualizar ambos por separado.
     */
    public void applyMove(Move move) {
        Color mover = board.turn();
        Duration elapsed = Duration.between(lastMoveTimestamp, Instant.now(clock));
        Duration storedBeforeMove = mover == Color.WHITE ? whiteTimeRemaining : blackTimeRemaining;
        Duration remaining = storedBeforeMove.minus(elapsed).plus(increment);
        if (remaining.isNegative()) {
            remaining = Duration.ZERO;
        }

        if (mover == Color.WHITE) {
            whiteTimeRemaining = remaining;
        } else {
            blackTimeRemaining = remaining;
        }

        board.applyMove(move);
        lastMoveTimestamp = Instant.now(clock);
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

    /**
     * Tiempo inicial y el incremento del time control de esta partida — usados por
     * GameResultRecorder para etiquetar la partida guardada (p. ej. "5+3"), ya que
     * whiteTimeRemaining/blackTimeRemaining cambian con cada jugada y no sirven para
     * saber cuál era el time control original una vez la partida está en curso.
     */
    public Duration initialTime() {
        return initialTime;
    }

    public Duration increment() {
        return increment;
    }

    /**
     * ¿A qué color pertenece este playerId dentro de esta partida?
     *
     * @throws IllegalArgumentException si playerId no es ninguno de los dos jugadores
     */
    public Color colorOf(String playerId) {
        if (whitePlayerId.equals(playerId)) {
            return Color.WHITE;
        }
        if (blackPlayerId.equals(playerId)) {
            return Color.BLACK;
        }
        throw new IllegalArgumentException("playerId no pertenece a esta partida: " + playerId);
    }

    public void markDisconnected(Color color) {
        if (color == Color.WHITE) {
            whiteDisconnectedAt = Instant.now(clock);
        } else {
            blackDisconnectedAt = Instant.now(clock);
        }
    }

    public void markConnected(Color color) {
        if (color == Color.WHITE) {
            whiteDisconnectedAt = null;
        } else {
            blackDisconnectedAt = null;
        }
    }

    /**
     * ¿Lleva `color` desconectado más de `gracePeriod`? false tanto si está conectado
     * como si nunca se ha desconectado (mismo estado: sin timestamp de desconexión).
     */
    public boolean hasExceededDisconnectGracePeriod(Color color, Duration gracePeriod) {
        Instant disconnectedAt = color == Color.WHITE ? whiteDisconnectedAt : blackDisconnectedAt;
        if (disconnectedAt == null) {
            return false;
        }
        return Duration.between(disconnectedAt, Instant.now(clock)).compareTo(gracePeriod) >= 0;
    }
}
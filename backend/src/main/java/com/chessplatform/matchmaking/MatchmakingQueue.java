package com.chessplatform.matchmaking;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class MatchmakingQueue {

    public record WaitingPlayer(String playerId, String username, int rating, TimeControl timeControl, Instant queuedAt) {
        public Duration waitingTime(Instant now) {
            return Duration.between(queuedAt, now);
        }
    }

    private final ConcurrentLinkedQueue<WaitingPlayer> queue = new ConcurrentLinkedQueue<>();

    // Un solo constructor implícito (sin argumentos) a propósito: MatchmakingQueue es un
    // @Component gestionado por Spring, así que evitamos cualquier ambigüedad de
    // autowiring con un segundo constructor. Ver setClock() más abajo para tests.
    private Clock clock = Clock.systemUTC();

    public void enqueue(String playerId, String username, int rating, TimeControl timeControl) {
        remove(playerId); // evita duplicados si ya estaba esperando (p. ej. doble click)
        queue.add(new WaitingPlayer(playerId, username, rating, timeControl, Instant.now(clock)));
    }

    public void remove(String playerId) {
        queue.removeIf(p -> p.playerId().equals(playerId));
    }

    public boolean removeAll(List<WaitingPlayer> players) {
        return queue.removeAll(players);
    }

    public List<WaitingPlayer> snapshot() {
        return List.copyOf(queue);
    }

    /**
     * "Ahora" según el reloj interno de la cola — mismo reloj que usa para fijar
     * queuedAt. MatchmakingService lo usa para calcular tiempos de espera con una
     * referencia consistente (el reloj real en producción, uno controlado en tests) en
     * vez de llamar a Instant.now() por su cuenta y desincronizarse del reloj de aquí.
     */
    Instant now() {
        return Instant.now(clock);
    }

    /**
     * Uso exclusivo para tests — mismo patrón que el Clock inyectable de GameSession,
     * para poder controlar el paso del tiempo con precisión al probar cómo crece la
     * ventana de tolerancia de rating, sin depender de Thread.sleep() real.
     */
    void setClock(Clock clock) {
        this.clock = clock;
    }
}
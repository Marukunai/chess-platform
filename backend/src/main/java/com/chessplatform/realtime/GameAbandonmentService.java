package com.chessplatform.realtime;

import com.chessplatform.engine.Color;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Barrido periódico que declara abandono cuando un jugador lleva desconectado más de la
 * ventana de gracia — ver PlayerConnectionListener (quién detecta la desconexión) y
 * GameSession.hasExceededDisconnectGracePeriod().
 *
 * Distinto del timeout por reloj (GameTimeoutService): el abandono puede darse mucho
 * antes de que el reloj se agote, sobre todo en controles de tiempo largos — el reloj
 * sigue corriendo mientras un jugador está desconectado (es correcto que así sea, ver
 * ADR-004), pero no tiene sentido obligar al rival a esperar minutos u horas a que se
 * agote solo porque el otro se desconectó.
 */
@Service
public class GameAbandonmentService {

    private static final long TICK_INTERVAL_MS = 1000;
    private static final Duration GRACE_PERIOD = Duration.ofSeconds(30);

    private final GameSessionRegistry sessionRegistry;
    private final GameEndNotifier gameEndNotifier;

    public GameAbandonmentService(GameSessionRegistry sessionRegistry, GameEndNotifier gameEndNotifier) {
        this.sessionRegistry = sessionRegistry;
        this.gameEndNotifier = gameEndNotifier;
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    public void tick() {
        for (GameSession session : sessionRegistry.allSessions()) {
            synchronized (session) {
                boolean whiteAbandoned = session.hasExceededDisconnectGracePeriod(Color.WHITE, GRACE_PERIOD);
                boolean blackAbandoned = session.hasExceededDisconnectGracePeriod(Color.BLACK, GRACE_PERIOD);

                if (whiteAbandoned && blackAbandoned) {
                    // Los dos se fueron y ninguno volvió a tiempo — tablas, no una
                    // victoria arbitraria para quien se comprueba primero.
                    gameEndNotifier.endGame(session, "1/2-1/2", "abandonment");
                } else if (whiteAbandoned) {
                    gameEndNotifier.endGame(session, "0-1", "abandonment");
                } else if (blackAbandoned) {
                    gameEndNotifier.endGame(session, "1-0", "abandonment");
                }
            }
        }
    }
}
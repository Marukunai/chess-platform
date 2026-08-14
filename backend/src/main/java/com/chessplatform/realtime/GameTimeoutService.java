package com.chessplatform.realtime;

import com.chessplatform.engine.Color;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Barrido periódico que comprueba si al jugador en turno se le ha agotado el tiempo. El
 * reloj en sí es server-authoritative y se calcula bajo demanda (ver
 * GameSession.timeRemaining) — sin este barrido, nada convertiría "el reloj ya está a
 * 0" en el fin real de la partida hasta que alguien intentase mover.
 *
 * Solo hace falta comprobar el color en turno: el reloj del otro jugador no corre
 * mientras espera, así que nunca puede estar agotándose en ese momento.
 */
@Service
public class GameTimeoutService {

    private static final long TICK_INTERVAL_MS = 1000;

    private final GameSessionRegistry sessionRegistry;
    private final GameEndNotifier gameEndNotifier;

    public GameTimeoutService(GameSessionRegistry sessionRegistry, GameEndNotifier gameEndNotifier) {
        this.sessionRegistry = sessionRegistry;
        this.gameEndNotifier = gameEndNotifier;
    }

    @Scheduled(fixedRate = TICK_INTERVAL_MS)
    public void tick() {
        for (GameSession session : sessionRegistry.allSessions()) {
            // Este barrido corre en su propio hilo programado, totalmente
            // independiente de los hilos que procesan mensajes STOMP — sin
            // sincronizar sobre la partida, podría solaparse con una jugada
            // procesándose justo en ese instante (ver el mismo bloqueo en
            // GameWebSocketController).
            synchronized (session) {
                Color playerToMove = session.board().turn();
                if (session.isTimeout(playerToMove)) {
                    String result = playerToMove == Color.WHITE ? "0-1" : "1-0";
                    gameEndNotifier.endGame(session, result, "timeout");
                }
            }
        }
    }
}
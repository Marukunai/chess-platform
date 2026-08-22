package com.chessplatform.bot;

import com.chessplatform.engine.Move;
import com.chessplatform.realtime.GameSession;
import com.chessplatform.realtime.GameSessionRegistry;
import com.chessplatform.realtime.GameStateBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Después de aplicar la jugada de un humano (o de unirse a una partida ya en marcha),
 * decide si ahora le toca mover al bot y, si es así, se lo pide al motor y aplica su
 * respuesta por el MISMO camino de difusión (GameStateBroadcaster) que usaría
 * cualquier jugada humana — el cliente no tiene que distinguir "esto vino de un
 * humano" de "esto vino de un bot", es solo una jugada más en /topic/game/{gameId}.
 *
 * Llamarlo cuando no hace falta (partida normal entre humanos, o partida contra bot
 * pero todavía no es su turno) es barato y seguro — se limita a comprobar y salir, sin
 * ningún efecto secundario.
 */
@Component
public class BotMoveService {

    private static final Logger log = LoggerFactory.getLogger(BotMoveService.class);

    private final BotGameRegistry botGameRegistry;
    private final GameSessionRegistry sessionRegistry;
    private final GameStateBroadcaster gameStateBroadcaster;

    public BotMoveService(BotGameRegistry botGameRegistry, GameSessionRegistry sessionRegistry,
                          GameStateBroadcaster gameStateBroadcaster) {
        this.botGameRegistry = botGameRegistry;
        this.sessionRegistry = sessionRegistry;
        this.gameStateBroadcaster = gameStateBroadcaster;
    }

    public void maybeTriggerBotMove(GameSession session) {
        Optional<BotGameInfo> maybeBotInfo = botGameRegistry.find(session.gameId());
        if (maybeBotInfo.isEmpty()) {
            return; // no es una partida contra bot
        }
        BotGameInfo botInfo = maybeBotInfo.get();
        if (session.board().turn() != botInfo.botColor()) {
            return; // le toca al humano, no al bot
        }
        // La jugada que se acaba de aplicar podría haber terminado la partida sola
        // (jaque mate, tablas automáticas...) — GameEndNotifier ya habría quitado la
        // sesión del registro en ese caso. Sin esta comprobación, el bot intentaría
        // mover en una partida que ya no existe.
        if (sessionRegistry.find(session.gameId()).isEmpty()) {
            return;
        }

        String uciMove;
        try {
            uciMove = botInfo.engine().bestMove(session.board().toFen(), botInfo.difficulty().moveTimeMs());
        } catch (Exception e) {
            // Mejor dejar la partida esperando (el humano siempre puede rendirse o
            // pedir tablas para salir) que reventar el hilo que procesaba la jugada
            // humana por un fallo del proceso externo.
            log.error("Fallo al pedir jugada a Stockfish para la partida {}", session.gameId(), e);
            return;
        }
        if (uciMove == null) {
            log.warn("Stockfish no dio ninguna jugada para la partida {} (posición sin jugadas legales?)", session.gameId());
            return;
        }

        Move move;
        try {
            move = Move.fromUci(uciMove);
        } catch (IllegalArgumentException e) {
            log.error("Stockfish devolvió una jugada con formato inválido ({}) para la partida {}", uciMove, session.gameId());
            return;
        }
        if (!session.board().legalMoves().contains(move)) {
            log.error("Stockfish propuso una jugada ilegal ({}) para la partida {}", uciMove, session.gameId());
            return;
        }

        synchronized (session) {
            session.applyMove(move);
            gameStateBroadcaster.broadcastAndCheckEnd(session);
        }
    }
}
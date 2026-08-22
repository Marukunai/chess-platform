package com.chessplatform.realtime;

import com.chessplatform.engine.Board;
import com.chessplatform.engine.Color;
import com.chessplatform.engine.Move;
import com.chessplatform.realtime.dto.GameStateSyncMessage;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Difunde el estado de una partida por /topic/game/{gameId} y decide si, con la última
 * jugada aplicada, la partida debe terminar sola (tablas por 50 movimientos, por
 * repetición, jaque mate o ahogado) — extraído de GameWebSocketController (donde vivía
 * como broadcastUpdatedState, privado) porque BotMoveService necesita exactamente la
 * misma lógica después de aplicar la jugada del bot: la jugada de un humano y la de un
 * bot terminan en el mismo sitio del tablero, así que las dos deberían difundirse y
 * comprobarse de la misma forma, no con dos copias del mismo código.
 */
@Component
public class GameStateBroadcaster {

    private final SimpMessagingTemplate messagingTemplate;
    private final GameEndNotifier gameEndNotifier;

    public GameStateBroadcaster(SimpMessagingTemplate messagingTemplate, GameEndNotifier gameEndNotifier) {
        this.messagingTemplate = messagingTemplate;
        this.gameEndNotifier = gameEndNotifier;
    }

    /**
     * Calcula legalMoves() e isInCheck() UNA sola vez, manda SIEMPRE el estado
     * actualizado (para que el cliente vea la jugada que se acaba de hacer — incluida la
     * que da jaque mate — antes de que llegue el aviso de fin de partida) y decide con
     * eso si además hay que terminar la partida.
     */
    public void broadcastAndCheckEnd(GameSession session) {
        Board board = session.board();
        String gameId = session.gameId();

        List<Move> legalMoves = board.legalMoves();
        boolean inCheck = board.isInCheck(board.turn());

        // "+"/"#" en la notación de la última jugada — se calcula aquí, reutilizando el
        // legalMoves()/isInCheck() que de todas formas hace falta para decidir el fin de
        // partida, en vez de que Board tenga que repetir ese cálculo (caro: simula cada
        // jugada pseudo-legal) solo para anotar la notación en cada jugada.
        if (inCheck) {
            board.annotateLastMove(legalMoves.isEmpty() ? "#" : "+");
        }

        messagingTemplate.convertAndSend(
                "/topic/game/%s".formatted(gameId),
                GameStateSyncMessage.from(session, legalMoves, inCheck)
        );

        if (board.isDrawByFiftyMoveRule()) {
            gameEndNotifier.endGame(session, "1/2-1/2", "fifty-move-rule");
            return;
        }
        if (board.isDrawByRepetition()) {
            gameEndNotifier.endGame(session, "1/2-1/2", "threefold-repetition");
            return;
        }
        if (legalMoves.isEmpty()) {
            String result = inCheck
                    ? (board.turn() == Color.WHITE ? "0-1" : "1-0")
                    : "1/2-1/2";
            String reason = inCheck ? "checkmate" : "stalemate";

            gameEndNotifier.endGame(session, result, reason);
        }
    }
}
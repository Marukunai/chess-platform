package com.chessplatform.persistence;

import com.chessplatform.engine.Board;
import com.chessplatform.engine.Move;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Reconstruye una partida ya jugada a partir de la lista de jugadas en UCI guardada en
 * Game.moveList, reproduciéndolas sobre un tablero nuevo con el motor real (Board/Move,
 * el mismo que valida partidas en vivo) — así el cliente web solo necesita recorrer los
 * resultados y renderizarlos, sin reimplementar ninguna regla de ajedrez en JavaScript.
 */
@Component
public class GameReplayService {

    /**
     * fenPositions: una posición por jugada, más la inicial (longitud = jugadas + 1).
     * notation: la notación legible de cada jugada (p. ej. "Rxf6"), en el mismo orden —
     * ver Board.notationHistory(). Ambas se calculan en un único recorrido del historial,
     * reutilizando el mismo tablero.
     */
    public record ReplayResult(List<String> fenPositions, List<String> notation) {
    }

    public ReplayResult reconstructReplay(String moveListString) {
        List<String> fenPositions = new ArrayList<>();
        Board board = Board.initial();
        fenPositions.add(board.toFen()); // posición inicial, antes de cualquier jugada

        if (moveListString != null && !moveListString.isBlank()) {
            for (String uci : moveListString.split(" ")) {
                Move move = Move.fromUci(uci);
                board.applyMove(move);
                fenPositions.add(board.toFen());
            }
        }

        return new ReplayResult(fenPositions, board.notationHistory());
    }
}
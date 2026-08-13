package com.chessplatform.persistence;

import com.chessplatform.engine.Board;
import com.chessplatform.engine.Move;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Reconstruye la secuencia de posiciones FEN de una partida ya jugada, a partir de la
 * lista de jugadas en UCI guardada en Game.moveList. Reproduce las jugadas sobre un
 * tablero nuevo usando el motor real (Board/Move, el mismo que valida partidas en
 * vivo) — así el cliente web solo necesita recorrer un array de FEN y renderizarlos,
 * sin reimplementar ninguna regla de ajedrez en JavaScript.
 */
@Component
public class GameReplayService {

    public List<String> reconstructFenPositions(String moveListString) {
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

        return fenPositions;
    }
}
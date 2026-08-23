package com.chessplatform.puzzle.dto;

import java.util.List;

/**
 * sideToMove: "white" | "black" — de quién es el turno en la posición, quien tiene que
 * encontrar la jugada. legalMovesUci: el cliente web no tiene motor de reglas propio
 * (ver ADR-011), así que las jugadas legales de esta posición viajan con el puzzle.
 * previousFen/previousMoveUci: la posición justo ANTES del error que originó el puzzle,
 * y la jugada que llevó de ahí hasta aquí — para poder animarla al abrir el puzzle
 * ("así es como se llegó a esta posición"), null en el caso raro de que el puzzle sea
 * la posición inicial sin ninguna jugada de por medio.
 */
public record PuzzleResponse(String puzzleId, String fen, String sideToMove, int rating, List<String> legalMovesUci,
                             String previousFen, String previousMoveUci) {
}
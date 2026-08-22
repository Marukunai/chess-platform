package com.chessplatform.puzzle.dto;

import java.util.List;

/** sideToMove: "white" | "black" — de quién es el turno en la posición, quien tiene que encontrar la jugada. legalMovesUci: el cliente web no tiene motor de reglas propio (ver ADR-011), así que las jugadas legales de esta posición viajan con el puzzle. */
public record PuzzleResponse(String puzzleId, String fen, String sideToMove, int rating, List<String> legalMovesUci) {
}
package com.chessplatform.puzzle.dto;

/** moveUci: la jugada que el usuario cree que es la solución, en notación UCI ("e2e4"). */
public record PuzzleAttemptRequest(String moveUci) {
}
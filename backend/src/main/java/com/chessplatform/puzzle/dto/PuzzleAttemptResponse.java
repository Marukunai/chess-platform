package com.chessplatform.puzzle.dto;

/** solutionUci: la jugada correcta, se manda siempre (acertado o no) para que el cliente pueda mostrarla igualmente. ratingChange: cuánto cambió TU rating de puzzles con este intento en concreto, puede ser negativo. */
public record PuzzleAttemptResponse(boolean correct, String solutionUci, int newRating, int ratingChange) {
}
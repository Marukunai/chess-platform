package com.chessplatform.puzzle.dto;

/** solutionUci: la jugada correcta, se manda siempre (acertado o no) para que el cliente pueda mostrarla igualmente. resultingFen: la posición después de aplicar la jugada correcta, para que el tablero pueda enseñarla en vez de quedarse quieto. ratingChange: cuánto cambió TU rating de puzzles con este intento en concreto, puede ser negativo. */
public record PuzzleAttemptResponse(boolean correct, String solutionUci, String resultingFen, int newRating, int ratingChange) {
}
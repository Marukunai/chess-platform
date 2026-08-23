package com.chessplatform.puzzle.dto;

/**
 * moveUci: la jugada que el usuario cree que es la correcta para ESTE paso, en
 * notación UCI ("e2e4"). stepIndex: qué jugada del que resuelve es esta dentro de la
 * línea de solución (0-indexado — 0 es la primera, 1 la segunda si el puzzle tiene más
 * de una jugada). hintUsed: si se pidió una pista en algún momento de este intento —
 * acumulativo, el cliente debe seguir mandando true en los pasos siguientes una vez se
 * ha usado una, no solo en el paso concreto donde se pidió (ver PuzzleController, que
 * solo usa este valor de verdad al cerrar el intento).
 */
public record PuzzleAttemptRequest(String moveUci, int stepIndex, boolean hintUsed) {
}
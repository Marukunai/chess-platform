package com.chessplatform.puzzle.dto;

import java.util.List;

/**
 * correct: si ESTE paso concreto fue el correcto. done: si el intento entero ya se
 * cerró (falló en algún paso, o completó el último con éxito) — mientras done sea
 * false, el cliente debe seguir enviando pasos, no hay rating todavía.
 * opponentReplyUci: la respuesta forzada del rival tras un paso correcto que no cierra
 * el intento — para animarla antes de dejar el siguiente paso listo; null si done es
 * true o si no hay ninguna respuesta que animar.
 * resultingFen: la posición tras aplicar la línea de solución COMPLETA (no solo hasta
 * donde llegó el intento) cuando done es true — o la posición tras el paso + la
 * respuesta del rival cuando done es false, para seguir jugando.
 * legalMovesUci: las jugadas legales de la posición resultante, solo cuando done es
 * false — el cliente no tiene motor de reglas propio (ver ADR-011), así que sin esto
 * no podría dejarte intentar el siguiente paso. null cuando done es true (ya no hace
 * falta, el intento está cerrado).
 * solutionFenSequence: una posición por cada jugada de la línea de solución (empezando
 * por la del propio puzzle, sin ninguna jugada aplicada todavía) — para poder navegar
 * la línea completa jugada a jugada una vez resuelto el puzzle, con flechas
 * anterior/siguiente. Solo se manda cuando done es true.
 * solutionNotation: la notación legible de cada jugada de la línea ("Nf3+", no
 * "g1f3") — solutionUci sigue llevando la UCI en bruto porque el cliente la necesita
 * tal cual para poder reconstruir el tablero, pero para MOSTRAR la jugada hay que usar
 * esto, no aquello. Solo se manda cuando done es true.
 * solutionUci/newRating/ratingChange: null mientras done sea false (el intento sigue
 * abierto, no hay nada definitivo que dar todavía).
 */
public record PuzzleAttemptResponse(boolean correct, boolean done, String opponentReplyUci, String resultingFen,
                                    List<String> legalMovesUci, String solutionUci, List<String> solutionFenSequence,
                                    List<String> solutionNotation, Integer newRating, Integer ratingChange) {
}
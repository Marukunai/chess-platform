package com.chessplatform.bot;

/**
 * El resultado de pedirle al motor que piense sobre una posición — no solo la mejor
 * jugada, también cuánto de buena o mala es la posición en sí, necesario para detectar
 * "swings" tácticos grandes al analizar una partida ya jugada (ver puzzle/PuzzleGenerationService).
 *
 * centipawns y mateIn son mutuamente excluyentes — si hay un mate forzado detectado,
 * centipawns es null (un número de centésimas de peón no tiene sentido cuando ya hay un
 * mate visto); si no, mateIn es null. Los dos, desde el punto de vista de QUIEN MUEVE en
 * esta posición concreta — el propio protocolo UCI ya lo da así, no hay que darle la
 * vuelta aquí.
 */
public record EngineEvaluation(String bestMoveUci, Integer centipawns, Integer mateIn) {
}
package com.chessplatform.persistence.dto;

/** Ver GameAnalysisService.MoveAnalysis — mismo contenido, DTO aparte para no acoplar la capa REST a la interna del servicio. */
public record MoveAnalysisResponse(int moveNumber, String notation, Integer evalCentipawns, Integer evalMate,
                                   String classification) {
}
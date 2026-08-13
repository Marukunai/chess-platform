package com.chessplatform.realtime.dto;

import com.chessplatform.engine.Move;
import com.chessplatform.engine.PieceType;
import com.chessplatform.engine.Square;

public record MoveMessage(String gameId, String from, String to, String promotionType) {

    /**
     * Convierte este DTO de red al Move del motor de reglas. promotionType, si no es
     * null, debe coincidir con el nombre del enum PieceType ("QUEEN", "ROOK", "BISHOP",
     * "KNIGHT") — así es como Jackson serializa/deserializa enums de Java por defecto,
     * sin necesitar aquí un parser de letras UCI (eso vive en Move.fromUci(), para
     * cuando hable con Stockfish en Fase 2, un protocolo distinto).
     *
     * @throws IllegalArgumentException si from/to no son notación algebraica válida, o
     *         promotionType no coincide con ningún PieceType.
     */
    public Move toEngineMove() {
        Square fromSquare = Square.fromAlgebraic(from);
        Square toSquare = Square.fromAlgebraic(to);
        PieceType promotion = promotionType != null ? PieceType.valueOf(promotionType) : null;
        return new Move(fromSquare, toSquare, promotion);
    }
}
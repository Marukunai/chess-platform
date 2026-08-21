package com.chessplatform.rating;

/**
 * Las cuatro modalidades por las que se separa el rating — coinciden exactamente con
 * los nombres que ya devuelve TimeControl.presetNameFor(), a propósito: cualquier
 * partida real (emparejamiento, revancha o reto) siempre pasa por esa validación antes
 * de crear la GameSession, así que su control de tiempo siempre corresponde a una de
 * estas cuatro constantes — nunca "ninguna modalidad conocida" en la práctica.
 */
public enum GameMode {
    BULLET, BLITZ, RAPID, CLASSICAL
}
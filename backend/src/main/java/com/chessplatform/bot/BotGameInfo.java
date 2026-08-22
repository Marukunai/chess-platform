package com.chessplatform.bot;

import com.chessplatform.engine.Color;

/** Qué motor atiende esta partida, de qué color juega el bot, y a qué dificultad — lo que hace falta para decidir cuándo y cómo debe mover, ver BotMoveService. */
public record BotGameInfo(StockfishEngine engine, Color botColor, BotDifficulty difficulty) {
}
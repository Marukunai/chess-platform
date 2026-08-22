package com.chessplatform.bot;

import java.io.IOException;

/**
 * Punto de indirección para poder sustituir la creación de un StockfishEngine real por
 * un doble en los tests — StockfishEngine arranca un proceso de verdad dentro de su
 * propio constructor, así que no se puede instanciar directamente en un test unitario
 * sin tener el binario de Stockfish instalado en la máquina que corre los tests.
 * PlayVsBotController depende de esta interfaz, nunca de "new StockfishEngine(...)"
 * directamente.
 */
public interface StockfishEngineFactory {
    StockfishEngine create(String stockfishPath) throws IOException;
}
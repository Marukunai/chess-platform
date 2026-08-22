package com.chessplatform.bot;

import org.springframework.stereotype.Component;

import java.io.IOException;

/** La implementación real, usada en producción — arranca un proceso de Stockfish de verdad. */
@Component
public class DefaultStockfishEngineFactory implements StockfishEngineFactory {
    @Override
    public StockfishEngine create(String stockfishPath) throws IOException {
        return new StockfishEngine(stockfishPath);
    }
}
package com.chessplatform.bot;

import com.chessplatform.engine.Color;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BotGameRegistryTest {

    @Test
    void findReturnsEmptyForAGameThatWasNeverRegistered() {
        BotGameRegistry registry = new BotGameRegistry();

        assertThat(registry.find("no-existe")).isEmpty();
        assertThat(registry.isBotGame("no-existe")).isFalse();
    }

    @Test
    void findReturnsWhatWasRegistered() {
        BotGameRegistry registry = new BotGameRegistry();
        StockfishEngine engine = Mockito.mock(StockfishEngine.class);
        BotGameInfo info = new BotGameInfo(engine, Color.BLACK, BotDifficulty.MEDIUM);

        registry.register("game-1", info);

        assertThat(registry.find("game-1")).contains(info);
        assertThat(registry.isBotGame("game-1")).isTrue();
    }

    @Test
    void removeClosesTheEngineAndForgetsTheGame() {
        BotGameRegistry registry = new BotGameRegistry();
        StockfishEngine engine = Mockito.mock(StockfishEngine.class);
        registry.register("game-1", new BotGameInfo(engine, Color.WHITE, BotDifficulty.EASY));

        registry.remove("game-1");

        verify(engine).close();
        assertThat(registry.find("game-1")).isEmpty();
    }

    @Test
    void removeDoesNothingForAGameThatWasNeverRegistered() {
        BotGameRegistry registry = new BotGameRegistry();

        // No debería lanzar nada, ni intentar cerrar ningún motor que nunca existió —
        // pasa constantemente para partidas normales entre humanos, que nunca están en
        // este registro.
        registry.remove("partida-normal-sin-bot");
    }

    @Test
    void removeOnlyClosesTheEngineOfTheGameRemovedNotOthers() {
        BotGameRegistry registry = new BotGameRegistry();
        StockfishEngine engineOne = Mockito.mock(StockfishEngine.class);
        StockfishEngine engineTwo = Mockito.mock(StockfishEngine.class);
        registry.register("game-1", new BotGameInfo(engineOne, Color.WHITE, BotDifficulty.EASY));
        registry.register("game-2", new BotGameInfo(engineTwo, Color.BLACK, BotDifficulty.HARD));

        registry.remove("game-1");

        verify(engineOne).close();
        verify(engineTwo, never()).close();
        assertThat(registry.find("game-2")).isPresent();
    }
}
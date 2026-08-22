package com.chessplatform.puzzle;

import com.chessplatform.bot.StockfishEngine;
import com.chessplatform.bot.StockfishEngineFactory;
import com.chessplatform.persistence.entity.Puzzle;
import com.chessplatform.persistence.repository.PuzzleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Siembra un puñado de puzzles conocidos la primera vez que arranca la aplicación, para
 * que la pantalla de puzzles no esté vacía mientras se acumulan partidas de verdad —
 * PuzzleGenerationService solo genera puzzles DESPUÉS de que se jueguen partidas, así
 * que un despliegue recién levantado tardaría en tener contenido sin esto.
 *
 * A propósito, NO se inventa a mano cuál es "la jugada correcta" de cada secuencia — se
 * reutiliza PuzzleGenerationService.analyze() tal cual sobre cada secuencia conocida, y
 * es el propio motor quien decide si hay un error lo bastante grande y cuál es la mejor
 * respuesta, exactamente igual que con una partida real. Lo único que aporta esta clase
 * son las jugadas de apertura en sí (aperturas y trampas de ajedrez muy conocidas,
 * verificables por cualquiera), no ningún veredicto táctico propio.
 *
 * Idempotente: si ya hay algún puzzle sembrado (sourceGameId null), no vuelve a
 * intentarlo — @EventListener(ApplicationReadyEvent) en vez de @PostConstruct, mismo
 * motivo que BotAccountSeeder (contexto de Spring ya completamente listo). @Async
 * porque analizar varias secuencias con el motor tarda unos segundos, y no hay ninguna
 * razón para retrasar el arranque de la aplicación por esto.
 */
@Component
public class PuzzleSeeder {

    private static final Logger log = LoggerFactory.getLogger(PuzzleSeeder.class);

    // "Mate del pastor" (Scholar's Mate) — la trampa más conocida de todas: 3...Cf6??
    // desarrolla en vez de defender f7, permitiendo 4.Dxf7#. Se marca como "checkmate"
    // para que analyze() excluya la jugada de mate en sí como candidata (ya arreglado
    // ese bug) y encuentre el error real (3...Cf6??) en su lugar.
    private static final String SCHOLARS_MATE =
            "e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 h5f7";

    // "Mate de Légal" (Légal's Trap/Mate) — 4...Cf6 (o cualquier desarrollo normal)
    // deja la trampa preparada; si negras muerden con 5...Axd1 (capturar la dama,
    // parece ganar material gratis), viene 6.Axf7+ Re7 7.Cd5#. El error real que
    // debería encontrar el motor es justo esa captura codiciosa de la dama.
    private static final String LEGALS_TRAP =
            "e2e4 e7e5 g1f3 d7d6 f1c4 c8g4 b1c3 g8f6 f3e5 g4d1 c4f7 e8e7 c3d5";

    // moveList -> reason (o null si la secuencia no termina en mate/ahogado, y por
    // tanto su última posición sigue siendo un candidato válido como cualquier otra).
    private static final Map<String, String> SEED_SEQUENCES = Map.of(
            SCHOLARS_MATE, "checkmate",
            LEGALS_TRAP, "checkmate"
    );

    private final PuzzleRepository puzzleRepository;
    private final PuzzleGenerationService puzzleGenerationService;
    private final StockfishEngineFactory engineFactory;
    private final String stockfishPath;

    public PuzzleSeeder(PuzzleRepository puzzleRepository, PuzzleGenerationService puzzleGenerationService,
                        StockfishEngineFactory engineFactory, @Value("${stockfish.path:}") String stockfishPath) {
        this.puzzleRepository = puzzleRepository;
        this.puzzleGenerationService = puzzleGenerationService;
        this.engineFactory = engineFactory;
        this.stockfishPath = stockfishPath;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void seedPuzzles() {
        if (stockfishPath == null || stockfishPath.isBlank()) {
            return; // sin motor configurado no hay análisis posible — igual que PuzzleGenerationService
        }
        if (puzzleRepository.existsBySourceGameIdIsNull()) {
            return; // ya se sembró alguna vez — no repetirlo en cada arranque
        }

        for (Map.Entry<String, String> sequence : SEED_SEQUENCES.entrySet()) {
            try (StockfishEngine engine = engineFactory.create(stockfishPath)) {
                puzzleGenerationService.analyze(sequence.getKey(), sequence.getValue(), null, engine)
                        .ifPresentOrElse(
                                puzzleRepository::save,
                                () -> log.warn("La secuencia sembrada no dio ningún puzzle (sin error lo bastante grande detectado)")
                        );
            } catch (Exception e) {
                // Una secuencia que falle no debería impedir sembrar el resto — mismo
                // criterio de "esto es una mejora, no algo crítico" que ya usa
                // PuzzleGenerationService.
                log.warn("No se pudo analizar una secuencia sembrada", e);
            }
        }
    }
}
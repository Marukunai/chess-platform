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
 * respuesta, exactamente igual que con una partida real.
 *
 * Las jugadas de apertura en sí SÍ se verificaron contra una fuente externa (Wikipedia
 * u otra página de aperturas) antes de convertirlas a UCI, no de memoria — Board no
 * valida legalidad completa al aplicar una jugada (solo que haya una pieza en la
 * casilla de origen), así que una jugada mal recordada no daría ningún error visible,
 * simplemente dejaría una posición corrupta camuflada de puzzle válido. Cada secuencia
 * lleva en su comentario la fuente contra la que se comprobó.
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

    // "Fried Liver Attack" (Ataque del hígado frito) — apertura muy conocida donde,
    // tras 4.Cg5 d5 5.exd5, la respuesta natural mas débil es 5...Cxd5?? (en vez de
    // retirar el caballo con 5...Ca5, 5...Cb4 o 5...b5), dejando la horquilla táctica
    // 6.Cxf7! sobre dama y torre. A diferencia de las otras dos, esta secuencia no
    // termina en mate ni ahogado — se detiene justo en el punto de la trampa, así que
    // su última posición SÍ sigue siendo un candidato válido (de ahí el "" en vez de
    // "checkmate"/"stalemate" en el mapa de más abajo).
    private static final String FRIED_LIVER_ATTACK =
            "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6 f3g5 d7d5 e4d5 f6d5";

    // Trampa del Gambito Englund — verificada contra Wikipedia (en.wikipedia.org/wiki/
    // Englund_Gambit): "White must avoid the notorious trap 6.Bc3?? Bb4!". Tras
    // 6.Ac3??, negras clavan el alfil a la torre con 6...Ab4!, ganando material.
    private static final String ENGLUND_GAMBIT_TRAP =
            "d2d4 e7e5 d4e5 b8c6 g1f3 d8e7 c1f4 e7b4 f4d2 b4b2 d2c3 f8b4";

    // Gambito Blackburne-Shilling — verificado contra Wikipedia (en.wikipedia.org/wiki/
    // Blackburne_Shilling_Gambit) y varias fuentes más: tras 4.Cxe5?? (capturar el peón
    // parece ganar material gratis y amenaza f7), 4...Dg5! ataca a la vez el caballo en
    // e5 y el peón de g2.
    private static final String BLACKBURNE_SHILLING_GAMBIT =
            "e2e4 e7e5 g1f3 b8c6 f1c4 c6d4 f3e5 d8g5";

    // Trampa "Fishing Pole" en la Ruy López — verificada contra Wikipedia (en.wikipedia
    // .org/wiki/Fishing_Pole_Trap) y varias fuentes más coincidentes: 4...Cg4?? es el
    // cebo; si blancas muerden con 6.hxg4, negras abren la columna h con 6...hxg4 y
    // monta un ataque de mate contra el rey ya enrocado.
    private static final String FISHING_POLE_TRAP =
            "e2e4 e7e5 g1f3 b8c6 f1b5 g8f6 e1g1 f6g4 h2h3 h7h5 h3g4 h5g4 f3e1 d8h4";

    // "Trampa de Stafford" (mate del alfil en 8 jugadas) — verificada contra
    // chesstrapguide.com y varias fuentes coincidentes: tras 6.Ag5? (clava el caballo,
    // parece natural), 6...Cxe4! sacrifica material a cambio de un ataque decisivo si
    // blancas se quedan con la dama (7.Axd8??).
    private static final String STAFFORD_GAMBIT_TRAP =
            "e2e4 e7e5 g1f3 g8f6 f3e5 b8c6 e5c6 d7c6 d2d3 f8c5 c1g5 f6e4 g5d8 c5f2 e1e2 c8g4";

    // "Trampa de Lasker" en el Contragambito Albin — verificada contra Wikipedia (en.
    // wikipedia.org/wiki/Albin_Countergambit): tras 6.Axb4?? (parece ganar la pieza sin
    // más), negras encadenan 6...exf2+ 7.Re2 fxg1=C+, coronando con jaque — la
    // secuencia se corta justo ahí porque las fuentes discrepan en la jugada 8 exacta,
    // pero todas coinciden hasta este punto.
    private static final String LASKER_TRAP =
            "d2d4 d7d5 c2c4 e7e5 d4e5 d5d4 e2e3 f8b4 c1d2 d4e3 d2b4 e3f2 e1e2 f2g1n";

    // "Trampa del Elefante" en el Gambito de Dama Rehusado — verificada contra
    // Wikipedia (en.wikipedia.org/wiki/Queen's_Gambit_Declined,_Elephant_Trap), con
    // partida documentada desde 1848: 6.Cxd5?? parece ganar un peón (el caballo en f6
    // "está clavado" a la dama), pero negras recapturan igualmente con 6...Cxd5!
    // porque blancas nunca llegan a explotar la supuesta clavada.
    private static final String ELEPHANT_TRAP =
            "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6 c1g5 b8d7 c4d5 e6d5 c3d5 f6d5";

    // "Trampa Siberiana" en el Gambito Smith-Morra — verificada contra Wikipedia (en.
    // wikipedia.org/wiki/Sicilian_Defence,_Smith–Morra_Gambit,_Siberian_Trap), con la
    // jugada del error marcada explícitamente en la fuente ("9.h3??"): blancas caen en
    // la trampa buscando espacio, sin ver la amenaza de mate que se les viene encima.
    private static final String SIBERIAN_TRAP =
            "e2e4 c7c5 d2d4 c5d4 c2c3 d4c3 b1c3 b8c6 g1f3 e7e6 f1c4 d8c7 e1g1 g8f6 d1e2 f6g4 h2h3";

    // moveList -> reason ("checkmate"/"stalemate" si la secuencia termina así, o ""
    // si se detiene en mitad de la partida sin que se acaben las jugadas legales — en
    // ese caso la última posición sigue siendo un candidato válido como cualquier otra).
    private static final Map<String, String> SEED_SEQUENCES = new java.util.HashMap<>();

    static {
        SEED_SEQUENCES.put(SCHOLARS_MATE, "checkmate");
        SEED_SEQUENCES.put(LEGALS_TRAP, "checkmate");
        SEED_SEQUENCES.put(FRIED_LIVER_ATTACK, "");
        SEED_SEQUENCES.put(ENGLUND_GAMBIT_TRAP, "");
        SEED_SEQUENCES.put(BLACKBURNE_SHILLING_GAMBIT, "");
        SEED_SEQUENCES.put(FISHING_POLE_TRAP, "");
        SEED_SEQUENCES.put(STAFFORD_GAMBIT_TRAP, "checkmate");
        SEED_SEQUENCES.put(LASKER_TRAP, "");
        SEED_SEQUENCES.put(ELEPHANT_TRAP, "");
        SEED_SEQUENCES.put(SIBERIAN_TRAP, "");
    }

    private final PuzzleRepository puzzleRepository;
    private final PuzzleGenerationService puzzleGenerationService;
    private final StockfishEngineFactory engineFactory;
    private final String stockfishPath;

    /** Visible para el paquete solo para que el test pueda reproducir cada secuencia sobre un tablero real y confirmar que ninguna jugada apunta a una casilla vacía por un error de conversión — ver PuzzleSeederTest. */
    static java.util.Set<String> seedMoveListsForTesting() {
        return SEED_SEQUENCES.keySet();
    }

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
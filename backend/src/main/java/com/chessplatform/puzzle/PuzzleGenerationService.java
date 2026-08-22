package com.chessplatform.puzzle;

import com.chessplatform.bot.EngineEvaluation;
import com.chessplatform.bot.StockfishEngine;
import com.chessplatform.bot.StockfishEngineFactory;
import com.chessplatform.engine.Board;
import com.chessplatform.engine.Move;
import com.chessplatform.persistence.GameReplayService;
import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.Puzzle;
import com.chessplatform.persistence.repository.PuzzleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Tras cada partida (humana o contra bot, no se distingue: un error sigue siendo un
 * error, venga de quien venga), analiza toda la partida en busca del "swing" táctico
 * más grande — la jugada que más empeoró la posición de quien la hizo, comparada con lo
 * mejor que tenía disponible — y, si supera un umbral mínimo, guarda esa posición como
 * puzzle nuevo.
 *
 * El análisis en sí (analyze()) es reutilizado también por PuzzleSeeder para sembrar
 * puzzles a partir de secuencias de jugadas conocidas, no solo de partidas reales
 * guardadas — la misma lógica de detección sirve para las dos cosas.
 *
 * @Async a propósito: analizar una partida entera (una evaluación del motor por cada
 * posición) tarda varios segundos — hacerlo dentro de GameEndNotifier.endGame()
 * retrasaría el aviso de fin de partida a los dos jugadores, que no tiene nada que ver
 * con esto. Se dispara desde GameResultRecorder justo después de guardar la partida, y
 * lo que pase aquí (éxito, fallo, ningún error lo bastante grande...) no vuelve a
 * comunicarse a nadie de forma síncrona — el puzzle, si sale alguno, simplemente
 * aparece disponible para quien busque el siguiente a partir de ahora.
 */
@Service
public class PuzzleGenerationService {

    private static final Logger log = LoggerFactory.getLogger(PuzzleGenerationService.class);

    // Ni muy corto (no daría tiempo a que el motor viera más allá de lo obvio) ni muy
    // largo (con N+1 posiciones por partida, cada milisegundo de más se multiplica) —
    // 300ms es suficiente para detectar errores claros sin que analizar una partida
    // entera se alargue más de unos pocos segundos.
    private static final int EVAL_MOVETIME_MS = 300;

    // En centésimas de peón — por debajo de esto se considera "una jugada subóptima
    // normal", no un error lo bastante claro como para merecer un puzzle. 300 centipawns
    // equivale, a grandes rasgos, a perder un peón y algo más de ventaja posicional.
    private static final int BLUNDER_THRESHOLD_CENTIPAWNS = 300;

    // Un mate no tiene un valor numérico "real" en centésimas de peón, pero para poder
    // comparar swings de forma uniforme con posiciones sin mate, se trata como una
    // puntuación extrema — muy por encima de cualquier evaluación material posible, pero
    // conservando el orden entre "mate en 1" (mejor) y "mate en 10" (peor, aunque
    // siga siendo mate).
    private static final int MATE_SCORE_BASE = 100_000;

    private final PuzzleRepository puzzleRepository;
    private final GameReplayService gameReplayService;
    private final StockfishEngineFactory engineFactory;
    private final String stockfishPath;

    public PuzzleGenerationService(PuzzleRepository puzzleRepository, GameReplayService gameReplayService,
                                   StockfishEngineFactory engineFactory, @Value("${stockfish.path:}") String stockfishPath) {
        this.puzzleRepository = puzzleRepository;
        this.gameReplayService = gameReplayService;
        this.engineFactory = engineFactory;
        this.stockfishPath = stockfishPath;
    }

    @Async
    public void generateFromGame(Game game) {
        if (stockfishPath == null || stockfishPath.isBlank()) {
            return; // sin motor configurado no hay análisis posible — no es un error, solo no se generan puzzles
        }
        if (game.getMoveList() == null || game.getMoveList().isBlank()) {
            return; // partida sin ninguna jugada real (no debería pasar, por seguridad)
        }

        try (StockfishEngine engine = engineFactory.create(stockfishPath)) {
            analyze(game.getMoveList(), game.getReason(), game.getId(), engine)
                    .ifPresent(puzzleRepository::save);
        } catch (Exception e) {
            // Generar puzzles es una mejora sobre lo que ya existe, no algo de lo que
            // dependa el resto de la plataforma — un fallo aquí (el motor se cae a
            // media partida, un timeout...) no debería propagarse a ningún sitio más.
            log.warn("No se pudo analizar la partida {} para generar un puzzle", game.getId(), e);
        }
    }

    /**
     * El análisis en sí, separado de generateFromGame() para que PuzzleSeeder pueda
     * reutilizarlo tal cual sobre secuencias de jugadas conocidas (no partidas reales
     * guardadas) — la misma lógica sirve para las dos cosas, la única diferencia es de
     * dónde viene la lista de jugadas y qué sourceGameId lleva el resultado (null para
     * los sembrados, ver Puzzle.sourceGameId).
     *
     * @param sourceGameId el id de la partida de origen, o null si viene de una
     *                      secuencia sembrada a mano (ver PuzzleSeeder)
     * @return el puzzle detectado, o vacío si no hubo ningún error lo bastante grande
     */
    Optional<Puzzle> analyze(String moveList, String reason, String sourceGameId, StockfishEngine engine) throws java.io.IOException {
        GameReplayService.ReplayResult replay = gameReplayService.reconstructReplay(moveList);
        List<String> positions = replay.fenPositions(); // posición 0 = inicial, posición i = tras la jugada i

        if (positions.size() < 2) {
            return Optional.empty();
        }

        // La última posición NO puede ser candidata a puzzle si terminó sin ninguna
        // jugada legal de por medio (jaque mate o ahogado) — no habría ninguna
        // "solución" que dar. Para cualquier otro motivo de fin (rendición, tiempo,
        // tablas... o null, para las secuencias sembradas que no terminan de verdad,
        // simplemente se detienen en un punto interesante), esa posición SÍ sigue
        // siendo un candidato válido como cualquier otra.
        boolean lastPositionHasNoLegalMoves = "checkmate".equals(reason) || "stalemate".equals(reason);
        int exclusiveUpperBound = lastPositionHasNoLegalMoves ? positions.size() - 1 : positions.size();

        // La evaluación de "después de la jugada anterior" ES la evaluación de "antes
        // de la jugada siguiente" — la misma posición, el mismo jugador en turno. Se
        // reutiliza de una iteración a la siguiente en vez de volver a pedírsela al
        // motor, así solo hace falta una evaluación por posición (N+1 en total para
        // una partida de N jugadas), no dos.
        EngineEvaluation currentEval = engine.evaluate(positions.get(0), EVAL_MOVETIME_MS);

        int biggestDrop = 0;
        int puzzlePositionIndex = -1;

        for (int i = 1; i < exclusiveUpperBound; i++) {
            int evalBeforeForMover = toComparableScore(currentEval);

            currentEval = engine.evaluate(positions.get(i), EVAL_MOVETIME_MS);
            int evalAfterForMover = -toComparableScore(currentEval); // la nueva evaluación es desde el punto de vista del RIVAL, se invierte para comparar

            int drop = evalBeforeForMover - evalAfterForMover; // positivo == la posición empeoró para quien acaba de mover
            if (drop >= BLUNDER_THRESHOLD_CENTIPAWNS && drop > biggestDrop) {
                biggestDrop = drop;
                puzzlePositionIndex = i;
            }
        }

        if (puzzlePositionIndex == -1) {
            return Optional.empty(); // ningún error lo bastante grande — no pasa nada, no todas las partidas (ni secuencias) dan un puzzle
        }

        String puzzleFen = positions.get(puzzlePositionIndex);
        EngineEvaluation puzzleEval = engine.evaluate(puzzleFen, EVAL_MOVETIME_MS);
        if (puzzleEval.bestMoveUci() == null) {
            return Optional.empty(); // no debería pasar (ya excluimos la única posición que podría no tener jugadas legales), pero por seguridad
        }

        // Reconstruir el tablero real justo hasta la posición del puzzle (no toda la
        // partida otra vez con el motor, solo aplicar jugadas sobre un tablero nuevo,
        // barato) para poder sacar sus jugadas legales — el cliente web no tiene
        // ningún motor de reglas propio (ver ADR-011), así que el puzzle tiene que
        // llevar esta información consigo, calculada aquí una sola vez.
        Board board = Board.initial();
        List<Move> moves = parseMoveList(moveList);
        for (int i = 0; i < puzzlePositionIndex; i++) {
            board.applyMove(moves.get(i));
        }
        String legalMovesUci = board.legalMoves().stream().map(Move::toUci).collect(Collectors.joining(" "));

        return Optional.of(new Puzzle(sourceGameId, puzzleFen, sideToMoveFromFen(puzzleFen),
                puzzleEval.bestMoveUci(), legalMovesUci));
    }

    private List<Move> parseMoveList(String moveList) {
        return Arrays.stream(moveList.trim().split("\\s+")).map(Move::fromUci).toList();
    }

    private int toComparableScore(EngineEvaluation eval) {
        if (eval.mateIn() != null) {
            return eval.mateIn() > 0 ? MATE_SCORE_BASE - eval.mateIn() : -MATE_SCORE_BASE - eval.mateIn();
        }
        return eval.centipawns() != null ? eval.centipawns() : 0;
    }

    /** El segundo campo de un FEN es "w" o "b" — de quién es el turno en esa posición. */
    private String sideToMoveFromFen(String fen) {
        String[] parts = fen.split(" ");
        return parts.length > 1 && "b".equals(parts[1]) ? "black" : "white";
    }
}
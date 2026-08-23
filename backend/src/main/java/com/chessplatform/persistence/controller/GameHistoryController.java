package com.chessplatform.persistence.controller;

import com.chessplatform.persistence.GameAnalysisService;
import com.chessplatform.persistence.GameReplayService;
import com.chessplatform.persistence.dto.GameAnalysisResponse;
import com.chessplatform.persistence.dto.GameDetailResponse;
import com.chessplatform.persistence.dto.GameSummaryResponse;
import com.chessplatform.persistence.dto.MoveAnalysisResponse;
import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.repository.GameRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

/**
 * Historial de partidas — de lectura pública a propósito, como en cualquier plataforma
 * de ajedrez real (lichess, chess.com): revisar partidas ajenas es parte normal de
 * estudiar rivales o simplemente mirar. Por eso vive fuera de "anyRequest().authenticated()"
 * en SecurityConfig, y por eso no hace falta el JwtAuthenticationFilter (todavía
 * pendiente) para que esto funcione.
 */
@RestController
@RequestMapping("/api/games")
public class GameHistoryController {

    private static final Logger log = LoggerFactory.getLogger(GameHistoryController.class);

    private final GameRepository gameRepository;
    private final GameReplayService replayService;
    private final GameAnalysisService analysisService;

    public GameHistoryController(GameRepository gameRepository, GameReplayService replayService,
                                 GameAnalysisService analysisService) {
        this.gameRepository = gameRepository;
        this.replayService = replayService;
        this.analysisService = analysisService;
    }

    @GetMapping("/user/{userId}")
    public List<GameSummaryResponse> historyForUser(@PathVariable String userId) {
        return gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc(userId, userId).stream()
                .map(this::toSummary)
                .toList();
    }

    @GetMapping("/{gameId}")
    public GameDetailResponse gameDetail(@PathVariable String gameId) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partida no encontrada"));
        return toDetail(game);
    }

    /**
     * Deliberadamente síncrono y sin caché — ver el javadoc de GameAnalysisService.
     * Puede tardar varios segundos en partidas largas; el cliente debe mostrar un
     * estado de carga mientras espera, no es un fallo si tarda.
     */
    @GetMapping("/{gameId}/analysis")
    public GameAnalysisResponse gameAnalysis(@PathVariable String gameId) {
        if (!analysisService.isAvailable()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El análisis con motor no está disponible ahora mismo");
        }
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Partida no encontrada"));

        List<GameAnalysisService.MoveAnalysis> analysis;
        try {
            analysis = analysisService.analyze(game.getMoveList());
        } catch (IOException e) {
            log.error("No se pudo analizar la partida {}", gameId, e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo analizar la partida");
        }

        List<MoveAnalysisResponse> moves = analysis.stream()
                .map(m -> new MoveAnalysisResponse(m.moveNumber(), m.notation(), m.evalCentipawns(), m.evalMate(), m.classification()))
                .toList();
        return new GameAnalysisResponse(gameId, moves);
    }

    private GameSummaryResponse toSummary(Game game) {
        return new GameSummaryResponse(
                game.getId(),
                game.getWhitePlayer().getId(),
                game.getWhitePlayer().getUsername(),
                game.getBlackPlayer().getId(),
                game.getBlackPlayer().getUsername(),
                game.getResult(),
                game.getReason(),
                game.getTimeControl(),
                game.getPlayedAt().toString(),
                game.getWhiteRatingChange(),
                game.getBlackRatingChange()
        );
    }

    private GameDetailResponse toDetail(Game game) {
        GameReplayService.ReplayResult replay = replayService.reconstructReplay(game.getMoveList());

        return new GameDetailResponse(
                game.getId(),
                game.getWhitePlayer().getUsername(),
                game.getBlackPlayer().getUsername(),
                game.getResult(),
                game.getReason(),
                game.getTimeControl(),
                game.getPlayedAt().toString(),
                replay.notation(),
                replay.fenPositions()
        );
    }
}
package com.chessplatform.persistence.controller;

import com.chessplatform.persistence.GameReplayService;
import com.chessplatform.persistence.dto.GameDetailResponse;
import com.chessplatform.persistence.dto.GameSummaryResponse;
import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.repository.GameRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

    private final GameRepository gameRepository;
    private final GameReplayService replayService;

    public GameHistoryController(GameRepository gameRepository, GameReplayService replayService) {
        this.gameRepository = gameRepository;
        this.replayService = replayService;
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

    private GameSummaryResponse toSummary(Game game) {
        return new GameSummaryResponse(
                game.getId(),
                game.getWhitePlayer().getId(),
                game.getWhitePlayer().getUsername(),
                game.getBlackPlayer().getId(),
                game.getBlackPlayer().getUsername(),
                game.getResult(),
                game.getTimeControl(),
                game.getPlayedAt().toString()
        );
    }

    private GameDetailResponse toDetail(Game game) {
        GameReplayService.ReplayResult replay = replayService.reconstructReplay(game.getMoveList());

        return new GameDetailResponse(
                game.getId(),
                game.getWhitePlayer().getUsername(),
                game.getBlackPlayer().getUsername(),
                game.getResult(),
                game.getTimeControl(),
                game.getPlayedAt().toString(),
                replay.notation(),
                replay.fenPositions()
        );
    }
}
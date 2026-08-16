package com.chessplatform.persistence.controller;

import com.chessplatform.persistence.dto.LeaderboardEntryResponse;
import com.chessplatform.persistence.dto.UserProfileResponse;
import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.GameRepository;
import com.chessplatform.persistence.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Perfiles y clasificación — de lectura pública a propósito, igual que el historial (ver
 * GameHistoryController): consultar el perfil o el ranking de cualquiera es normal en
 * cualquier plataforma de ajedrez real.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;
    private final GameRepository gameRepository;

    public UserController(UserRepository userRepository, GameRepository gameRepository) {
        this.userRepository = userRepository;
        this.gameRepository = gameRepository;
    }

    @GetMapping("/leaderboard")
    public List<LeaderboardEntryResponse> leaderboard() {
        List<User> topPlayers = userRepository.findTop50ByOrderByRatingDesc();
        return IntStream.range(0, topPlayers.size())
                .mapToObj(i -> {
                    User user = topPlayers.get(i);
                    return new LeaderboardEntryResponse(i + 1, user.getId(), user.getUsername(),
                            (int) Math.round(user.getRating()));
                })
                .toList();
    }

    @GetMapping("/{userId}")
    public UserProfileResponse profile(@PathVariable String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        List<Game> games = gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc(userId, userId);

        int wins = 0;
        int losses = 0;
        int draws = 0;
        int winsByCheckmate = 0;
        for (Game game : games) {
            if ("1/2-1/2".equals(game.getResult())) {
                draws++;
                continue;
            }
            boolean userIsWhite = userId.equals(game.getWhitePlayer().getId());
            boolean whiteWon = "1-0".equals(game.getResult());
            boolean userWon = userIsWhite == whiteWon;
            if (userWon) {
                wins++;
                if ("checkmate".equals(game.getReason())) {
                    winsByCheckmate++;
                }
            } else {
                losses++;
            }
        }

        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                (int) Math.round(user.getRating()),
                (int) Math.round(user.getRatingDeviation()),
                games.size(),
                wins,
                losses,
                draws,
                winsByCheckmate
        );
    }
}
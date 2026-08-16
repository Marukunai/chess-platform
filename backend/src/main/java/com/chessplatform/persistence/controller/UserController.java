package com.chessplatform.persistence.controller;

import com.chessplatform.persistence.dto.LeaderboardEntryResponse;
import com.chessplatform.persistence.dto.UpdateProfileRequest;
import com.chessplatform.persistence.dto.UserProfileResponse;
import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.GameRepository;
import com.chessplatform.persistence.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Perfiles y clasificación. Consultar (GET) es de lectura pública a propósito, igual
 * que el historial (ver GameHistoryController): ver el perfil o el ranking de
 * cualquiera es normal en cualquier plataforma de ajedrez real. Editar (PUT) sí
 * necesita identidad de verdad — ver SecurityConfig y JwtAuthenticationFilter, que
 * existen justo por este endpoint.
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
        return toProfileResponse(user);
    }

    /**
     * authentication viene de JwtAuthenticationFilter, que solo puebla el
     * SecurityContext si el JWT es válido — pero cualquiera con un JWT válido podría
     * intentar editar el perfil de OTRO usuario cambiando el {userId} de la URL, así que
     * hace falta comprobar aquí que la identidad autenticada coincide con el perfil que
     * se intenta editar, no basta con exigir "estar autenticado con algo".
     */
    @PutMapping("/{userId}")
    public UserProfileResponse updateProfile(@PathVariable String userId, @RequestBody UpdateProfileRequest request,
                                             Authentication authentication) {
        if (authentication == null || !userId.equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo puedes editar tu propio perfil");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));

        String newUsername = request.username() == null ? "" : request.username().trim();
        if (newUsername.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El nombre de usuario no puede estar vacío");
        }
        if (!newUsername.equals(user.getUsername()) && userRepository.findByUsername(newUsername).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ese nombre de usuario ya está en uso");
        }

        user.updateProfile(newUsername, blankToNull(request.country()), blankToNull(request.avatarUrl()));
        userRepository.save(user);

        return toProfileResponse(user);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private UserProfileResponse toProfileResponse(User user) {
        List<Game> games = gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc(user.getId(), user.getId());

        int wins = 0;
        int losses = 0;
        int draws = 0;
        int winsByCheckmate = 0;
        for (Game game : games) {
            if ("1/2-1/2".equals(game.getResult())) {
                draws++;
                continue;
            }
            boolean userIsWhite = user.getId().equals(game.getWhitePlayer().getId());
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
                user.getCountry(),
                user.getAvatarUrl(),
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
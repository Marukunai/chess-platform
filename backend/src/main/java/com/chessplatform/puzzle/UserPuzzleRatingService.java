package com.chessplatform.puzzle;

import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.entity.UserPuzzleRating;
import com.chessplatform.persistence.repository.UserPuzzleRatingRepository;
import org.springframework.stereotype.Component;

/**
 * Mismo patrón exacto que UserRatingService (rating de partidas) — "dame el rating de
 * puzzles de este usuario, o uno nuevo sin guardar con los valores por defecto si
 * todavía no existe". No guarda nada aquí a propósito: quien de verdad necesite
 * persistirlo (PuzzleController, tras resolver o fallar un intento) lo guarda él mismo
 * después de actualizarlo — evita el mismo guardado duplicado que ya se corrigió una
 * vez en UserRatingService.
 */
@Component
public class UserPuzzleRatingService {

    private final UserPuzzleRatingRepository repository;

    public UserPuzzleRatingService(UserPuzzleRatingRepository repository) {
        this.repository = repository;
    }

    public UserPuzzleRating findOrDefault(User user) {
        return repository.findByUser_Id(user.getId())
                .orElseGet(() -> new UserPuzzleRating(user));
    }
}
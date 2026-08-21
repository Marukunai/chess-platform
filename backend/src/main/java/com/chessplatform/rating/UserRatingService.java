package com.chessplatform.rating;

import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.entity.UserRating;
import com.chessplatform.persistence.repository.UserRatingRepository;
import org.springframework.stereotype.Component;

/**
 * Punto único para "dame el rating de este usuario en esta modalidad, o los valores por
 * defecto de Glicko-2 si todavía no tiene ninguno" — tanto MatchmakingController (para
 * saber con qué rating entrar a la cola) como GameResultRecorder (para actualizarlo
 * tras una partida) pasan por aquí.
 *
 * A propósito NO guarda nada en base de datos cuando no existe fila todavía — devuelve
 * una UserRating nueva sin persistir, con los valores por defecto. Guardar aquí sería
 * prematuro: MatchmakingController solo necesita el número para encolar (ni falta que
 * la fila exista todavía si al final ni siquiera llega a jugar), y GameResultRecorder
 * va a guardarla de todas formas en cuanto actualice el rating tras la partida — guardar
 * aquí ADEMÁS de ahí sería una escritura doble en cada primera partida de cada
 * modalidad, sin ningún beneficio real.
 */
@Component
public class UserRatingService {

    private final UserRatingRepository userRatingRepository;

    public UserRatingService(UserRatingRepository userRatingRepository) {
        this.userRatingRepository = userRatingRepository;
    }

    public UserRating findOrDefault(User user, GameMode mode) {
        return userRatingRepository.findByUser_IdAndMode(user.getId(), mode)
                .orElseGet(() -> new UserRating(user, mode));
    }
}
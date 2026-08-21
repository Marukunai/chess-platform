package com.chessplatform.persistence.repository;

import com.chessplatform.persistence.entity.UserRating;
import com.chessplatform.rating.GameMode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRatingRepository extends JpaRepository<UserRating, String> {

    Optional<UserRating> findByUser_IdAndMode(String userId, GameMode mode);

    // Todas las modalidades que este usuario tiene con al menos una fila — como
    // UserRating solo se guarda de verdad cuando GameResultRecorder graba una partida
    // jugada en esa modalidad (ver ADR correspondiente), esto es exactamente "en qué
    // modalidades ha jugado alguna vez", útil para los logros de "juega tu primera
    // partida de bullet/blitz/rápidas/clásicas".
    List<UserRating> findByUser_Id(String userId);

    // DeletedAtIsNull en la relación, mismo motivo que ya tenía el ranking único
    // anterior: una cuenta borrada no debe aparecer en ningún ranking.
    List<UserRating> findTop50ByModeAndUser_DeletedAtIsNullOrderByRatingDesc(GameMode mode);
}
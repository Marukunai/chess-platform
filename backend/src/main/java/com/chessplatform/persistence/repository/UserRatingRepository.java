package com.chessplatform.persistence.repository;

import com.chessplatform.persistence.entity.UserRating;
import com.chessplatform.rating.GameMode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRatingRepository extends JpaRepository<UserRating, String> {

    Optional<UserRating> findByUser_IdAndMode(String userId, GameMode mode);

    // DeletedAtIsNull en la relación, mismo motivo que ya tenía el ranking único
    // anterior: una cuenta borrada no debe aparecer en ningún ranking.
    List<UserRating> findTop50ByModeAndUser_DeletedAtIsNullOrderByRatingDesc(GameMode mode);
}
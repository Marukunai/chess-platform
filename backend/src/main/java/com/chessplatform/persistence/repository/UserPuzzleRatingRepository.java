package com.chessplatform.persistence.repository;

import com.chessplatform.persistence.entity.UserPuzzleRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPuzzleRatingRepository extends JpaRepository<UserPuzzleRating, String> {
    Optional<UserPuzzleRating> findByUser_Id(String userId);

    // Mismo criterio que el ranking de rating de partidas (DeletedAtIsNull +
    // BotFalse) — ver ADR sobre por qué se excluyen los bots de todos los rankings.
    List<UserPuzzleRating> findTop50ByUser_DeletedAtIsNullAndUser_BotFalseOrderByRatingDesc();
}
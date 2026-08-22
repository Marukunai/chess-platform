package com.chessplatform.persistence.repository;

import com.chessplatform.persistence.entity.UserPuzzleRating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPuzzleRatingRepository extends JpaRepository<UserPuzzleRating, String> {
    Optional<UserPuzzleRating> findByUser_Id(String userId);
}
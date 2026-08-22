package com.chessplatform.persistence.repository;

import com.chessplatform.persistence.entity.UserPuzzleAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPuzzleAttemptRepository extends JpaRepository<UserPuzzleAttempt, String> {
    boolean existsByUser_IdAndPuzzle_Id(String userId, String puzzleId);

    /** Para el logro de puzzles resueltos — solo cuenta los que se acertaron, no todos los intentos. */
    long countByUser_IdAndSolvedTrue(String userId);
}
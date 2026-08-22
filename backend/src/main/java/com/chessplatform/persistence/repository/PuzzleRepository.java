package com.chessplatform.persistence.repository;

import com.chessplatform.persistence.entity.Puzzle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PuzzleRepository extends JpaRepository<Puzzle, String> {

    /**
     * Los puzzles que este usuario todavía NO ha intentado, ordenados por cercanía a
     * su rating actual — el primero de la lista es el candidato ideal (el más
     * parecido a su nivel), igual que el emparejamiento por rating normal. Se piden
     * varios (no solo uno) por si el más cercano resultara no servir por lo que sea
     * (no debería pasar, pero deja margen sin tener que repetir la consulta entera).
     */
    @Query("SELECT p FROM Puzzle p WHERE p.id NOT IN "
            + "(SELECT a.puzzle.id FROM UserPuzzleAttempt a WHERE a.user.id = :userId) "
            + "ORDER BY ABS(p.rating - :targetRating) ASC")
    List<Puzzle> findClosestByRatingExcludingAttemptedByUser(@Param("userId") String userId,
                                                             @Param("targetRating") double targetRating);
}
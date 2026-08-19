package com.chessplatform.persistence.repository;

import com.chessplatform.persistence.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);

    // DeletedAtIsNull en vez de un findTop50ByOrderByRatingDesc() sin más — una cuenta
    // borrada (ver User.anonymizeForDeletion) no debe aparecer en el ranking.
    List<User> findTop50ByDeletedAtIsNullOrderByRatingDesc();
}
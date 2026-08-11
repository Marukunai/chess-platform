package com.chessplatform.persistence.repository;

import com.chessplatform.persistence.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GameRepository extends JpaRepository<Game, String> {
}

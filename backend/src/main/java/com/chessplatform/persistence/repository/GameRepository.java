package com.chessplatform.persistence.repository;

import com.chessplatform.persistence.entity.Game;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameRepository extends JpaRepository<Game, String> {

    /**
     * Partidas donde el usuario jugó de blancas o de negras, más recientes primero.
     * Llamar con el mismo userId en ambos argumentos.
     */
    List<Game> findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc(String whitePlayerId, String blackPlayerId);
}
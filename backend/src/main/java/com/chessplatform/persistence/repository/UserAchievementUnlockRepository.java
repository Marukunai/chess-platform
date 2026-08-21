package com.chessplatform.persistence.repository;

import com.chessplatform.persistence.entity.UserAchievementUnlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserAchievementUnlockRepository extends JpaRepository<UserAchievementUnlock, String> {

    List<UserAchievementUnlock> findByUser_Id(String userId);

    // Para AchievementUnlockService.checkAndNotify() — comprobar si UN logro concreto
    // ya estaba desbloqueado antes de intentar guardarlo de nuevo (la restricción
    // UNIQUE de la tabla ya lo impediría, pero comprobarlo antes evita el intento de
    // guardado fallido en el camino feliz, que es el que pasa la inmensa mayoría de veces).
    Optional<UserAchievementUnlock> findByUser_IdAndAchievementId(String userId, String achievementId);

    // Para "quién fue el primero" — la fila más antigua entre TODOS los usuarios para
    // este logro en concreto, sin importar de quién es la petición.
    Optional<UserAchievementUnlock> findFirstByAchievementIdOrderByUnlockedAtAsc(String achievementId);

    // Para la rareza — cuántos han desbloqueado este logro en concreto, entre cuentas
    // activas (una cuenta borrada no debería contar para el porcentaje que ve todo el mundo).
    long countByAchievementIdAndUser_DeletedAtIsNull(String achievementId);
}
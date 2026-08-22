package com.chessplatform.persistence.repository;

import com.chessplatform.persistence.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);

    // Para el buscador de amigos — ContainingIgnoreCase es "LIKE %texto%" sin
    // distinguir mayúsculas, y DeletedAtIsNull excluye cuentas borradas por el mismo
    // motivo que en el ranking: no tiene sentido poder encontrar ni añadir a alguien
    // que ya no existe de verdad.
    List<User> findTop20ByUsernameContainingIgnoreCaseAndDeletedAtIsNull(String usernameFragment);

    // Para el ranking global de logros (AchievementService) y para la rareza de cada
    // logro — BotFalse excluye a las cuentas de bot (ver User.bot) de los dos: no son
    // personas jugando de verdad, no tiene sentido que aparezcan en un ranking entre
    // jugadores ni que cuenten para el porcentaje de "qué parte de los jugadores tiene
    // este logro" (nunca se les comprueban logros — ver GameEndNotifier — así que
    // dejarlos en el denominador solo abarataría artificialmente la rareza de todo).
    List<User> findByDeletedAtIsNullAndBotFalse();

    long countByDeletedAtIsNullAndBotFalse();
}
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
}
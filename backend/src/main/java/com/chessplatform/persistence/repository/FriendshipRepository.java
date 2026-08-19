package com.chessplatform.persistence.repository;

import com.chessplatform.persistence.entity.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends JpaRepository<Friendship, String> {

    /**
     * La relación (en el estado que sea) entre estos dos usuarios, sin importar quién
     * fue el requester y quién el addressee — @Query en vez de derivación por nombre
     * porque hace falta comprobar el MISMO par de ids en las dos direcciones a la vez,
     * algo que el nombrado automático de Spring Data no expresa con claridad.
     */
    @Query("SELECT f FROM Friendship f WHERE "
            + "(f.requester.id = :userA AND f.addressee.id = :userB) "
            + "OR (f.requester.id = :userB AND f.addressee.id = :userA)")
    Optional<Friendship> findBetween(@Param("userA") String userA, @Param("userB") String userB);

    /** Solicitudes pendientes QUE HAN LLEGADO a este usuario (es el addressee) — no las que él mismo envió. */
    List<Friendship> findByAddressee_IdAndStatus(String addresseeId, String status);

    /** Todas las amistades ya aceptadas de este usuario, sea cual sea su papel en cada una. */
    @Query("SELECT f FROM Friendship f WHERE f.status = 'ACCEPTED' "
            + "AND (f.requester.id = :userId OR f.addressee.id = :userId)")
    List<Friendship> findAcceptedFriendships(@Param("userId") String userId);
}
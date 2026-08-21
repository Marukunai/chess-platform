package com.chessplatform.persistence.repository;

import com.chessplatform.persistence.entity.DirectMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, String> {

    /** La conversación completa entre estos dos, en cualquiera de las dos direcciones, de más antiguo a más reciente. */
    @Query("SELECT m FROM DirectMessage m WHERE "
            + "(m.sender.id = :userA AND m.recipient.id = :userB) "
            + "OR (m.sender.id = :userB AND m.recipient.id = :userA) "
            + "ORDER BY m.sentAt ASC")
    List<DirectMessage> findConversation(@Param("userA") String userA, @Param("userB") String userB);

    // Para el logro "Charlatán" — cuántos mensajes directos ha enviado en total,
    // contando solo los que él mandó, no los que recibió.
    long countBySender_Id(String senderId);

    // Para el logro "Popular" — el reverso del anterior, cuántos le han mandado A ÉL.
    long countByRecipient_Id(String recipientId);

    // Para el logro "Red social" — con cuántas personas DISTINTAS ha intercambiado
    // aunque sea un mensaje, sin importar quién empezó la conversación cada vez. El
    // CASE elige "la otra persona" según de qué lado esté el usuario en cada fila.
    @Query("SELECT COUNT(DISTINCT CASE WHEN m.sender.id = :userId THEN m.recipient.id ELSE m.sender.id END) "
            + "FROM DirectMessage m WHERE m.sender.id = :userId OR m.recipient.id = :userId")
    long countDistinctConversationPartners(@Param("userId") String userId);
}
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
}
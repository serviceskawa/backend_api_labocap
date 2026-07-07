package com.labo.anapath.messaging;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatRepository extends JpaRepository<Chat, UUID> {

    @Query("""
            SELECT c FROM Chat c
            WHERE c.sender.id = :userId OR c.receiver.id = :userId
            ORDER BY c.createdAt DESC
            """)
    Page<Chat> findAllByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            SELECT c FROM Chat c
            WHERE (c.sender.id = :u1 AND c.receiver.id = :u2)
               OR (c.sender.id = :u2 AND c.receiver.id = :u1)
            ORDER BY c.createdAt ASC
            """)
    Page<Chat> findConversation(@Param("u1") UUID u1, @Param("u2") UUID u2, Pageable pageable);

    List<Chat> findByReceiverIdAndSenderIdAndIsReadFalse(UUID receiverId, UUID senderId);

    /** Nombre total de messages non lus reçus par l'utilisateur. */
    long countByReceiverIdAndIsReadFalse(UUID receiverId);
}

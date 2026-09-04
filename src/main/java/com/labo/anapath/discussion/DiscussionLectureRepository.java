package com.labo.anapath.discussion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DiscussionLectureRepository
        extends JpaRepository<DiscussionLecture, DiscussionLecture.Clef> {

    /**
     * Les messages d'un fil que cette personne n'a pas encore lus.
     *
     * <p>Sert à ne poser que les lignes manquantes en ouvrant le fil :
     * réécrire les lectures déjà connues ferait autant d'écritures inutiles
     * qu'il y a de messages, à chaque ouverture.</p>
     */
    @Query(value = """
            SELECT m.id FROM discussion_messages m
            LEFT JOIN discussion_lectures l
                 ON l.message_id = m.id AND l.user_id = :userId
            WHERE m.discussion_id = :discussionId
              AND m.author_id <> :userId
              AND l.message_id IS NULL
            """, nativeQuery = true)
    List<UUID> nonLusDuFil(@Param("discussionId") UUID discussionId,
                           @Param("userId") UUID userId);
}

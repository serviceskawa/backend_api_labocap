package com.labo.anapath.discussion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Les dépôts du fil de discussion, groupés : ils ne servent qu'ensemble. */
public final class DiscussionRepositories {

    private DiscussionRepositories() {}

    @Repository
    public interface Discussions extends JpaRepository<Discussion, UUID> {
        Optional<Discussion> findByTestOrderId(UUID testOrderId);
    }

    @Repository
    public interface Messages extends JpaRepository<DiscussionMessage, UUID> {

        List<DiscussionMessage> findByDiscussionIdOrderByCreatedAtAsc(UUID discussionId);

        /**
         * Ce que cette personne n'a pas lu, dossier par dossier.
         *
         * <p>Ses propres messages sont écartés : on ne se notifie pas soi-même,
         * et un badge qui s'allume en écrivant ferait douter de tous les
         * autres.</p>
         *
         * <p>Ne compte que les fils où elle participe. Un médecin tagué sur un
         * cas qui n'est pas le sien y a été ajouté comme participant à ce
         * moment-là : la règle d'adressage de la maquette se traduit ainsi, une
         * seule fois, au lieu d'être rejouée à chaque lecture.</p>
         *
         * @return lignes {@code [testOrderId, nombre de non-lus]}
         */
        @Query(value = """
                SELECT d.test_order_id, COUNT(m.id)
                FROM discussion_messages m
                JOIN discussions d ON d.id = m.discussion_id
                JOIN discussion_participants p
                     ON p.discussion_id = d.id AND p.user_id = :userId
                LEFT JOIN discussion_lectures l
                     ON l.message_id = m.id AND l.user_id = :userId
                WHERE m.author_id <> :userId
                  AND l.message_id IS NULL
                GROUP BY d.test_order_id
                """, nativeQuery = true)
        List<Object[]> compterNonLus(@Param("userId") UUID userId);
    }

    @Repository
    public interface Participants extends JpaRepository<DiscussionParticipant, UUID> {
        Optional<DiscussionParticipant> findByDiscussionIdAndUserId(UUID discussionId, UUID userId);
    }

    @Repository
    public interface Lectures extends JpaRepository<DiscussionLecture, DiscussionLecture.Clef> {

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
}

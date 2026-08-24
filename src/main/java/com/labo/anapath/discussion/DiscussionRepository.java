package com.labo.anapath.discussion;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Les fils de discussion.
 *
 * <p>Interface de premier niveau, et non imbriquée dans une classe qui les
 * regrouperait : Spring Data ne scanne pas les interfaces internes — son
 * {@code considerNestedRepositoryInterfaces} vaut faux par défaut. Elles
 * compilent, l'application démarre jusqu'au bout du démarrage, puis échoue à
 * injecter un dépôt qui n'existe pas.</p>
 */
@Repository
public interface DiscussionRepository extends JpaRepository<Discussion, UUID> {

    Optional<Discussion> findByTestOrderId(UUID testOrderId);
}

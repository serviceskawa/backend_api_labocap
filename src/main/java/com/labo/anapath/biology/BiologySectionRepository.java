package com.labo.anapath.biology;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository JPA pour {@link BiologySection}.
 *
 * <p>Les requêtes sont automatiquement filtrées par {@code deleted_at IS NULL}
 * grâce à la restriction Hibernate définie sur l'entité.</p>
 */
@Repository
public interface BiologySectionRepository extends JpaRepository<BiologySection, UUID> {

    /**
     * Sections actives d'une analyse, dans l'ordre d'affichage.
     *
     * @param labTestId identifiant de l'analyse
     * @return sections triées par position
     */
    List<BiologySection> findByLabTest_IdOrderByPositionAsc(UUID labTestId);
}

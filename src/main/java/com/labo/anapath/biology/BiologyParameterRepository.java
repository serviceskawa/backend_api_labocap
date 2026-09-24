package com.labo.anapath.biology;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository JPA pour {@link BiologyParameter}.
 *
 * <p>Les requêtes sont automatiquement filtrées par {@code deleted_at IS NULL}
 * grâce à la restriction Hibernate définie sur l'entité.</p>
 */
@Repository
public interface BiologyParameterRepository extends JpaRepository<BiologyParameter, UUID> {

    /**
     * Paramètres actifs d'une analyse, dans l'ordre d'affichage, unité chargée
     * d'emblée (elle figure sur chaque ligne de la fiche).
     *
     * @param labTestId identifiant de l'analyse
     * @return paramètres triés par position
     */
    @EntityGraph(attributePaths = "unitMeasurement")
    List<BiologyParameter> findByLabTest_IdOrderByPositionAsc(UUID labTestId);
}

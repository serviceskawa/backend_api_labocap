package com.labo.anapath.contract;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA pour l'entité {@link DetailsContrat}.
 *
 * <p>Les lignes tarifaires sont gérées en cascade depuis {@link Contrat}
 * (orphanRemoval = true) ; ce repository est disponible pour des accès
 * directs ponctuels si nécessaire.</p>
 */
@Repository
public interface DetailsContratRepository extends JpaRepository<DetailsContrat, UUID> {

    /**
     * Recherche la ligne tarifaire correspondant à un couple contrat / analyse.
     *
     * @param contratId  identifiant du contrat
     * @param labTestId  identifiant de l'analyse
     * @return la ligne tarifaire si elle existe, sinon {@link Optional#empty()}
     */
    Optional<DetailsContrat> findByContratIdAndLabTestId(UUID contratId, UUID labTestId);

    /** Règle tarifaire par catégorie (remise en pourcentage appliquée à toute la catégorie). */
    Optional<DetailsContrat> findByContratIdAndCategoryTestId(UUID contratId, UUID categoryTestId);
}

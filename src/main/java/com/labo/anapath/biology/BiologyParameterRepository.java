package com.labo.anapath.biology;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    /**
     * Libellés de paramètres, <b>y compris retirés du catalogue</b>.
     *
     * <p>Le nom d'un paramètre n'est pas copié sur la valeur saisie : une valeur
     * dont le paramètre a été retiré retrouve son libellé par la ligne supprimée
     * logiquement, toujours lisible par son identifiant. Requête native, pour
     * échapper à la restriction {@code deleted_at IS NULL} de l'entité.</p>
     *
     * @param ids identifiants de paramètres (non vide)
     * @return un libellé par identifiant connu
     */
    @Query(value = "SELECT CAST(p.id AS VARCHAR) AS id, p.name AS name "
            + "FROM biology_parameters p WHERE p.id IN (:ids)", nativeQuery = true)
    List<LibelleDeParametre> findLibellesYComprisRetires(@Param("ids") Collection<UUID> ids);

    /** Libellé d'un paramètre, retiré ou non. */
    interface LibelleDeParametre {
        String getId();
        String getName();
    }
}

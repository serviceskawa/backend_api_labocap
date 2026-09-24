package com.labo.anapath.biology;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repository JPA pour {@link BiologyReferenceRange}.
 *
 * <p>Les requêtes sont automatiquement filtrées par {@code deleted_at IS NULL}
 * grâce à la restriction Hibernate définie sur l'entité.</p>
 */
@Repository
public interface BiologyReferenceRangeRepository extends JpaRepository<BiologyReferenceRange, UUID> {

    /**
     * Valeurs de référence actives d'un paramètre, dans l'ordre d'affichage.
     *
     * @param parameterId identifiant du paramètre
     * @return plages triées par position
     */
    List<BiologyReferenceRange> findByParameter_IdOrderByPositionAsc(UUID parameterId);

    /**
     * Valeurs de référence actives de plusieurs paramètres en une requête
     * (chargement d'une fiche complète).
     *
     * @param parameterIds identifiants des paramètres
     * @return plages triées par position
     */
    List<BiologyReferenceRange> findByParameter_IdInOrderByPositionAsc(Collection<UUID> parameterIds);
}

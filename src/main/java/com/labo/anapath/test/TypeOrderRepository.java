package com.labo.anapath.test;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA pour l'entité {@link TypeOrder}.
 *
 * <p>Les requêtes sont automatiquement filtrées par {@code deleted_at IS NULL}
 * grâce à la restriction Hibernate définie sur l'entité.</p>
 */
@Repository
public interface TypeOrderRepository extends JpaRepository<TypeOrder, UUID> {

    /**
     * Retourne la liste paginée des types de bons d'une succursale donnée.
     *
     * @param branchId identifiant de la succursale
     * @param pageable paramètres de pagination et de tri
     * @return page de types de bons
     */
    Page<TypeOrder> findByBranchId(UUID branchId, Pageable pageable);

    List<TypeOrder> findAllByBranchIdOrderByTitle(UUID branchId);

    /**
     * Recherche un type de bon par son identifiant et sa succursale.
     * Assure l'isolation multi-tenant.
     *
     * @param id       identifiant UUID du type de bon
     * @param branchId identifiant de la succursale
     * @return le type de bon s'il appartient à la succursale, sinon vide
     */
    java.util.Optional<TypeOrder> findByIdAndBranchId(UUID id, UUID branchId);

    /**
     * Recherche un type de bon par son slug technique.
     * Utilisé lors de la création de bons de demande pour identifier le type.
     *
     * @param slug slug du type de bon
     * @return un {@link Optional} contenant le type trouvé, ou vide
     */
    Optional<TypeOrder> findBySlug(String slug);

    /**
     * Vérifie si un type de bon avec ce slug existe dans la succursale (insensible à la casse).
     * Utilisé pour détecter les doublons lors de la création.
     *
     * @param slug     slug à vérifier
     * @param branchId identifiant de la succursale
     * @return {@code true} si le slug est déjà utilisé dans la succursale
     */
    boolean existsBySlugIgnoreCaseAndBranchId(String slug, UUID branchId);

    /**
     * Vérifie si un type de bon avec ce slug existe dans la succursale, en excluant
     * celui identifié par {@code id}. Utilisé pour détecter les doublons lors d'une mise à jour.
     *
     * @param slug     slug à vérifier
     * @param branchId identifiant de la succursale
     * @param id       identifiant du type à exclure
     * @return {@code true} si le slug est déjà utilisé par un autre type
     */
    boolean existsBySlugIgnoreCaseAndBranchIdAndIdNot(String slug, UUID branchId, UUID id);

    /**
     * Retourne les identifiants des types de bons immuno (slugs
     * {@code immuno-interne} et {@code immuno-exterme}) pour une branche.
     *
     * <p>Utilisé pour filtrer les bons d'examen de la section Immunohistochimie.</p>
     *
     * @param branchId identifiant de la succursale
     * @return liste d'UUID des types immuno (vide si aucun)
     */
    @Query("SELECT t.id FROM TypeOrder t WHERE t.branchId = :branchId " +
            "AND LOWER(t.slug) IN ('immuno-interne', 'immuno-exterme')")
    List<UUID> findImmunoTypeIds(@Param("branchId") UUID branchId);

    /**
     * Retourne les identifiants des types de bons d'anatomo-cytopathologie
     * (slugs {@code cytologie} et {@code histologie}) pour une branche.
     *
     * <p>Utilisé par le compteur du badge « Demandes d'examen » du menu, qui reprend
     * le helper Laravel {@code getnbrTestOrderpending()}.</p>
     *
     * @param branchId identifiant de la succursale
     * @return liste d'UUID des types cytologie/histologie (vide si aucun)
     */
    @Query("SELECT t.id FROM TypeOrder t WHERE t.branchId = :branchId " +
            "AND LOWER(t.slug) IN ('cytologie', 'histologie')")
    List<UUID> findCytoHistoTypeIds(@Param("branchId") UUID branchId);
}

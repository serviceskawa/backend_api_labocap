package com.labo.anapath.test;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository JPA pour l'entité {@link LabTest}.
 *
 * <p>Les requêtes sont automatiquement filtrées par {@code deleted_at IS NULL}
 * grâce à la restriction Hibernate définie sur l'entité.</p>
 */
@Repository
public interface LabTestRepository extends JpaRepository<LabTest, UUID> {

    /**
     * Retourne la liste paginée des analyses d'une succursale donnée.
     *
     * @param branchId identifiant de la succursale
     * @param pageable paramètres de pagination et de tri
     * @return page d'analyses
     */
    Page<LabTest> findByBranchId(UUID branchId, Pageable pageable);

    /**
     * Retourne la liste paginée des analyses d'une succursale en appliquant des filtres
     * optionnels sur le nom (recherche partielle, insensible à la casse) et le statut.
     *
     * <p>Un paramètre {@code null} désactive le filtre correspondant.</p>
     *
     * @param branchId identifiant de la succursale
     * @param search   terme de recherche partielle sur le nom (ou {@code null})
     * @param status   statut exact à filtrer, ACTIF/INACTIF (ou {@code null})
     * @param pageable paramètres de pagination et de tri
     * @return page d'analyses correspondant aux filtres
     */
    @Query("""
            SELECT t FROM LabTest t
            WHERE t.branchId = :branchId
              AND (CAST(:search AS string) IS NULL
                   OR FUNCTION('unaccent', LOWER(t.name))
                      LIKE FUNCTION('unaccent', LOWER(CONCAT('%', CAST(:search AS string), '%'))))
              AND (CAST(:status AS string) IS NULL OR t.status = :status)
            """)
    Page<LabTest> findByFilters(@Param("branchId") UUID branchId,
                                @Param("search") String search,
                                @Param("status") String status,
                                Pageable pageable);

    List<LabTest> findAllByBranchIdOrderByName(UUID branchId);

    /** Analyses de la succursale, triées du plus récemment créé au plus ancien. */
    List<LabTest> findAllByBranchIdOrderByCreatedAtDesc(UUID branchId);

    /**
     * Recherche une analyse par son identifiant et sa succursale.
     * Assure l'isolation multi-tenant.
     *
     * @param id       identifiant UUID de l'analyse
     * @param branchId identifiant de la succursale
     * @return l'analyse si elle appartient à la succursale, sinon vide
     */
    java.util.Optional<LabTest> findByIdAndBranchId(UUID id, UUID branchId);

    /**
     * Recherche les analyses dont le nom contient le terme donné, dans une succursale.
     * Utilisé pour l'autocomplétion dans les formulaires de demande.
     *
     * <p>Insensible aux accents autant qu'à la casse : le catalogue mêle les deux
     * orthographes — « HYSTERECTOMIE » et « HYSTÉRECTOMIE » y coexistent, comme
     * « BIOPSIES DU SEIN » et ses variantes accentuées. Une dérivation Spring Data
     * (« ContainingIgnoreCase ») ne replie que la casse : le médecin qui tapait
     * l'accent ne voyait pas les entrées sans, et réciproquement.</p>
     *
     * @param name     terme de recherche (partiel, insensible casse et accents)
     * @param branchId identifiant de la succursale
     * @return liste des analyses correspondantes
     */
    @Query("""
            SELECT t FROM LabTest t
            WHERE t.branchId = :branchId
              AND FUNCTION('unaccent', LOWER(t.name))
                  LIKE FUNCTION('unaccent', LOWER(CONCAT('%', CAST(:name AS string), '%')))
            """)
    List<LabTest> findByNameContainingIgnoreCaseAndBranchId(@Param("name") String name,
                                                            @Param("branchId") UUID branchId);

    /**
     * Vérifie si une analyse portant ce nom existe dans la succursale (insensible à la casse).
     * Utilisé pour détecter les doublons lors de la création.
     *
     * @param name     nom de l'analyse
     * @param branchId identifiant de la succursale
     * @return {@code true} si le nom est déjà utilisé
     */
    boolean existsByNameIgnoreCaseAndBranchId(String name, UUID branchId);

    /**
     * Vérifie si une analyse portant ce nom existe dans la succursale, en excluant
     * celle identifiée par {@code id}. Utilisé pour détecter les doublons lors d'une mise à jour.
     *
     * @param name     nom de l'analyse
     * @param branchId identifiant de la succursale
     * @param id       identifiant de l'analyse à exclure
     * @return {@code true} si le nom est déjà utilisé par une autre analyse
     */
    boolean existsByNameIgnoreCaseAndBranchIdAndIdNot(String name, UUID branchId, UUID id);

    /**
     * Vérifie si au moins une analyse utilise l'unité de mesure donnée.
     * Utilisé pour bloquer la suppression d'une unité référencée.
     *
     * @param unitMeasurement unité de mesure à vérifier
     * @return {@code true} si l'unité est référencée par une analyse
     */
    boolean existsByUnitMeasurement(UnitMeasurement unitMeasurement);

    /**
     * Vérifie si au moins une analyse appartient à la catégorie donnée.
     * Utilisé pour bloquer la suppression d'une catégorie référencée.
     *
     * @param categoryTest catégorie à vérifier
     * @return {@code true} si la catégorie est référencée par une analyse
     */
    boolean existsByCategoryTest(CategoryTest categoryTest);

    // Dashboard KPIs
    long countByBranchId(UUID branchId);
}

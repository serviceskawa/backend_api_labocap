package com.labo.anapath.test;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository JPA pour l'entité {@link CategoryTest}.
 *
 * <p>Les requêtes sont automatiquement filtrées par {@code deleted_at IS NULL}
 * grâce à la restriction Hibernate définie sur l'entité.</p>
 */
@Repository
public interface CategoryTestRepository extends JpaRepository<CategoryTest, UUID> {

    /**
     * Retourne la liste paginée des catégories d'une succursale donnée.
     *
     * @param branchId identifiant de la succursale
     * @param pageable paramètres de pagination et de tri
     * @return page de catégories
     */
    Page<CategoryTest> findByBranchId(UUID branchId, Pageable pageable);

    /**
     * Vérifie si une catégorie portant ce nom existe dans la succursale (insensible à la casse).
     * Utilisé pour détecter les doublons lors de la création.
     *
     * @param name     nom de la catégorie
     * @param branchId identifiant de la succursale
     * @return {@code true} si le nom est déjà utilisé dans la succursale
     */
    boolean existsByNameIgnoreCaseAndBranchId(String name, UUID branchId);

    /**
     * Vérifie si une catégorie portant ce nom existe dans la succursale,
     * en excluant la catégorie identifiée par {@code id}.
     * Utilisé pour détecter les doublons lors d'une mise à jour.
     *
     * @param name     nom de la catégorie
     * @param branchId identifiant de la succursale
     * @param id       identifiant de la catégorie à exclure de la vérification
     * @return {@code true} si le nom est déjà utilisé par une autre catégorie
     */
    boolean existsByNameIgnoreCaseAndBranchIdAndIdNot(String name, UUID branchId, UUID id);

    /**
     * Vérifie si une catégorie portant ce code existe dans la succursale (insensible à la casse).
     * Utilisé pour détecter les doublons de code lors de la création.
     *
     * @param code     code de la catégorie
     * @param branchId identifiant de la succursale
     * @return {@code true} si le code est déjà utilisé dans la succursale
     */
    boolean existsByCodeIgnoreCaseAndBranchId(String code, UUID branchId);

    /**
     * Vérifie si une catégorie portant ce code existe dans la succursale,
     * en excluant la catégorie identifiée par {@code id}.
     * Utilisé pour détecter les doublons de code lors d'une mise à jour.
     *
     * @param code     code de la catégorie
     * @param branchId identifiant de la succursale
     * @param id       identifiant de la catégorie à exclure de la vérification
     * @return {@code true} si le code est déjà utilisé par une autre catégorie
     */
    boolean existsByCodeIgnoreCaseAndBranchIdAndIdNot(String code, UUID branchId, UUID id);
}

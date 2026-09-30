package com.labo.anapath.test;

import com.labo.anapath.common.Discipline;
import com.labo.anapath.common.dto.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Interface de service métier pour la gestion des catégories d'analyses.
 *
 * <p>Définit les opérations CRUD sur les catégories du catalogue d'analyses.</p>
 */
public interface CategoryTestService {

    /**
     * Retourne la liste paginée des catégories d'une succursale.
     *
     * @param page     numéro de page (0-indexé)
     * @param size     taille de la page
     * @param discipline discipline des catégories listées
     * @param branchId identifiant de la succursale
     * @return page de DTOs de catégories
     */
    PageResponse<CategoryTestResponseDto> findAll(int page, int size, Discipline discipline, UUID branchId);

    /**
     * Recherche une catégorie par son identifiant.
     *
     * @param id identifiant UUID de la catégorie
     * @return le DTO de la catégorie trouvée
     * @throws com.labo.anapath.common.exception.ResourceNotFoundException si la catégorie n'existe pas
     */
    CategoryTestResponseDto findById(UUID id);

    /**
     * Crée une nouvelle catégorie dans la succursale spécifiée.
     *
     * @param dto      données de création
     * @param branchId identifiant de la succursale
     * @return le DTO de la catégorie créée
     * @throws com.labo.anapath.common.exception.DuplicateResourceException si le nom existe déjà
     *         dans la discipline, ou si le code existe déjà dans la succursale
     * @throws com.labo.anapath.common.exception.BusinessException si la discipline est BIOLOGY
     *         alors que le module est désactivé
     */
    CategoryTestResponseDto create(CategoryTestRequestDto dto, UUID branchId);

    /**
     * Met à jour une catégorie existante.
     *
     * @param id  identifiant UUID de la catégorie
     * @param dto nouvelles données
     * @return le DTO mis à jour
     * @throws com.labo.anapath.common.exception.BusinessException si la requête change la discipline
     */
    CategoryTestResponseDto update(UUID id, CategoryTestRequestDto dto);

    /**
     * Supprime logiquement une catégorie (soft-delete).
     * La suppression est refusée si des analyses ({@link LabTest}) y sont rattachées.
     *
     * @param id identifiant UUID de la catégorie à supprimer
     * @throws com.labo.anapath.common.exception.BusinessException si la catégorie est référencée
     */
    void delete(UUID id);

    /**
     * Crée, pour la succursale, les catégories de biologie usuelles qui n'y existent
     * pas encore : Hématologie, Biochimie, Immunologie/Sérologie, Bactériologie,
     * Parasitologie. Idempotent — un second appel ne crée rien.
     *
     * <p>Le laboratoire déclenche ce geste depuis l'écran des catégories plutôt que
     * de subir un amorçage imposé : il reste libre de nommer ses catégories.</p>
     *
     * @param branchId identifiant de la succursale
     * @return les catégories de biologie de la succursale après l'opération, par nom
     * @throws com.labo.anapath.common.exception.BusinessException si le module Biologie est désactivé
     */
    List<CategoryTestResponseDto> createBiologyDefaults(UUID branchId);
}

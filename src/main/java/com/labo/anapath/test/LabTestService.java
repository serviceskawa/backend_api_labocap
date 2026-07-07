package com.labo.anapath.test;

import com.labo.anapath.common.dto.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Interface de service métier pour la gestion des analyses du catalogue du laboratoire.
 *
 * <p>Définit les opérations CRUD ainsi que la recherche par nom sur les analyses.</p>
 */
public interface LabTestService {

    /**
     * Retourne la liste paginée des analyses d'une succursale, avec filtres optionnels.
     *
     * @param page     numéro de page (0-indexé)
     * @param size     taille de la page
     * @param search   terme de recherche partielle sur le nom (ou {@code null}/vide)
     * @param status   statut exact à filtrer, ACTIF/INACTIF (ou {@code null}/vide)
     * @param branchId identifiant de la succursale
     * @return page de DTOs d'analyses
     */
    PageResponse<LabTestResponseDto> findAll(int page, int size, String search, String status, UUID branchId);

    List<LabTestResponseDto> findAll(UUID branchId);

    /**
     * Recherche une analyse par son identifiant.
     *
     * @param id identifiant UUID de l'analyse
     * @return le DTO de l'analyse trouvée
     * @throws com.labo.anapath.common.exception.ResourceNotFoundException si l'analyse n'existe pas
     */
    LabTestResponseDto findById(UUID id);

    /**
     * Recherche des analyses dont le nom contient le terme donné (insensible à la casse).
     *
     * @param query    terme de recherche partielle
     * @param branchId identifiant de la succursale
     * @return liste des analyses correspondantes
     */
    List<LabTestResponseDto> search(String query, UUID branchId);

    /**
     * Crée une nouvelle analyse dans la succursale spécifiée.
     *
     * @param dto      données de création
     * @param branchId identifiant de la succursale
     * @return le DTO de l'analyse créée
     * @throws com.labo.anapath.common.exception.DuplicateResourceException si le nom existe déjà
     */
    LabTestResponseDto create(LabTestRequestDto dto, UUID branchId);

    /**
     * Met à jour une analyse existante.
     *
     * @param id  identifiant UUID de l'analyse
     * @param dto nouvelles données
     * @return le DTO mis à jour
     */
    LabTestResponseDto update(UUID id, LabTestRequestDto dto);

    /**
     * Supprime logiquement une analyse (soft-delete).
     *
     * @param id identifiant UUID de l'analyse à supprimer
     */
    void delete(UUID id);
}

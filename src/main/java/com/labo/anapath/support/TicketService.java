package com.labo.anapath.support;

import com.labo.anapath.common.dto.PageResponse;

import java.util.UUID;

/**
 * Interface de service définissant les opérations métier sur les tickets de support interne.
 */
public interface TicketService {

    /**
     * Retourne la liste paginée des tickets d'une filiale.
     *
     * @param page     numéro de page
     * @param size     taille de la page
     * @param branchId identifiant de la filiale
     * @return page de tickets
     */
    PageResponse<TicketResponseDto> findAll(int page, int size, UUID branchId);

    /**
     * Compte les tickets encore ouverts pour le badge du menu — tous les tickets de la
     * filiale si l'utilisateur est super-admin, seulement les siens sinon (reprise du
     * helper Laravel {@code getnbrTicketPending()}).
     *
     * @param userId   identifiant de l'utilisateur connecté
     * @param branchId identifiant de la filiale
     * @return nombre de tickets ouverts
     */
    long countOpen(UUID userId, UUID branchId);

    /**
     * Retourne un ticket par son identifiant.
     *
     * @param id identifiant UUID du ticket
     * @return le ticket correspondant
     * @throws com.labo.anapath.common.exception.ResourceNotFoundException si introuvable
     */
    TicketResponseDto findById(UUID id);

    /**
     * Crée un nouveau ticket de support rattaché à un utilisateur et une filiale.
     *
     * @param dto      données du ticket
     * @param userId   identifiant de l'utilisateur créateur
     * @param branchId identifiant de la filiale
     * @return le ticket créé
     */
    TicketResponseDto create(TicketRequestDto dto, UUID userId, UUID branchId);

    /**
     * Met à jour le contenu d'un ticket (titre, description, priorité).
     *
     * @param id  identifiant UUID du ticket à modifier
     * @param dto nouvelles données
     * @return le ticket mis à jour
     */
    TicketResponseDto update(UUID id, TicketRequestDto dto);

    /**
     * Met à jour le statut d'un ticket dans le flux de résolution.
     *
     * @param id     identifiant UUID du ticket
     * @param status nouveau statut
     * @return le ticket avec le statut mis à jour
     */
    TicketResponseDto updateStatus(UUID id, TicketStatus status);

    /**
     * Supprime (logiquement) un ticket.
     *
     * @param id identifiant UUID du ticket à supprimer
     */
    void delete(UUID id);
}

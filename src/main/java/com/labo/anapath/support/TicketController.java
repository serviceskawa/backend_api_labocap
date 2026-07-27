package com.labo.anapath.support;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST exposant les opérations sur les tickets de support interne.
 * <p>
 * Toutes les routes sont préfixées par {@code /api/v1/tickets}.
 * La création et la consultation sont accessibles avec {@code view-support} ;
 * la gestion du statut et la suppression requièrent {@code manage-support}.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketCommentService ticketCommentService;

    /**
     * Retourne la liste paginée des tickets de la filiale de l'utilisateur connecté.
     *
     * @param page      numéro de page
     * @param size      taille de la page
     * @param principal utilisateur authentifié
     * @return page de tickets
     */
    @GetMapping
    @PreAuthorize("hasAuthority('view-tickets')")
    public ResponseEntity<ApiResponse<PageResponse<TicketResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(ticketService.findAll(page, size, principal.getBranchId())));
    }

    /**
     * Retourne le nombre de tickets encore ouverts, pour le badge « Signaler un
     * problème » du menu (helper Laravel {@code getnbrTicketPending()}) : tous les
     * tickets pour un super-admin, seulement les siens pour les autres.
     *
     * @param principal utilisateur authentifié
     * @return objet JSON {@code { "count": N }}
     */
    @GetMapping("/count-open")
    @PreAuthorize("hasAuthority('view-tickets')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> countOpen(
            @AuthenticationPrincipal UserPrincipal principal) {
        long count = ticketService.countOpen(principal.getId(), principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success(java.util.Map.of("count", count)));
    }

    /**
     * Retourne le détail d'un ticket par son identifiant.
     *
     * @param id identifiant UUID du ticket
     * @return le ticket correspondant
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-tickets')")
    public ResponseEntity<ApiResponse<TicketResponseDto>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(ticketService.findById(id)));
    }

    /**
     * Crée un nouveau ticket de support.
     * Le ticket est automatiquement rattaché à l'utilisateur connecté
     * et initialisé au statut {@link TicketStatus#OPEN}.
     *
     * @param dto       données du ticket à créer
     * @param principal utilisateur authentifié (créateur du ticket)
     * @return le ticket créé avec le statut HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAuthority('view-tickets')")
    public ResponseEntity<ApiResponse<TicketResponseDto>> create(
            @Valid @RequestBody TicketRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ticket créé", ticketService.create(dto, principal.getId(), principal.getBranchId())));
    }

    /**
     * Met à jour le contenu d'un ticket existant (titre, description, priorité).
     *
     * @param id  identifiant UUID du ticket à modifier
     * @param dto nouvelles données
     * @return le ticket mis à jour
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-tickets')")
    public ResponseEntity<ApiResponse<TicketResponseDto>> update(
            @PathVariable UUID id, @Valid @RequestBody TicketRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Ticket mis à jour", ticketService.update(id, dto)));
    }

    /**
     * Met à jour uniquement le statut d'un ticket (flux de résolution).
     * Cette action est réservée aux gestionnaires du support.
     *
     * @param id     identifiant UUID du ticket
     * @param status nouveau statut à appliquer
     * @return le ticket avec le statut mis à jour
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('edit-tickets')")
    public ResponseEntity<ApiResponse<TicketResponseDto>> updateStatus(
            @PathVariable UUID id,
            @RequestParam TicketStatus status) {
        return ResponseEntity.ok(ApiResponse.success("Statut mis à jour", ticketService.updateStatus(id, status)));
    }

    /**
     * Supprime (logiquement) un ticket par son identifiant.
     *
     * @param id identifiant UUID du ticket à supprimer
     * @return réponse vide confirmant la suppression
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-tickets')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        ticketService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Ticket supprimé", null));
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('view-tickets')")
    public ResponseEntity<ApiResponse<TicketCommentResponseDto>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody TicketCommentRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Commentaire ajouté",
                        ticketCommentService.create(id, dto, principal.getId(), principal.getBranchId())));
    }

    @GetMapping("/{id}/comments")
    @PreAuthorize("hasAuthority('view-tickets')")
    public ResponseEntity<ApiResponse<List<TicketCommentResponseDto>>> getComments(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(ticketCommentService.findByTicketId(id)));
    }
}

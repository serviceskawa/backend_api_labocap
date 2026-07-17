package com.labo.anapath.inventory;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Contrôleur REST gérant les mouvements de stock (entrées, sorties, ajustements).
 * <p>
 * Toutes les routes sont préfixées par {@code /api/v1/movements}.
 * Les mouvements sont immuables une fois créés : seuls la consultation
 * et la création sont exposées.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/movements")
@RequiredArgsConstructor
public class MovementController {

    private final MovementService movementService;

    /**
     * Retourne la liste paginée des mouvements de stock de la filiale connectée.
     *
     * @param page      numéro de page
     * @param size      taille de la page
     * @param principal utilisateur authentifié
     * @return page de mouvements de stock
     */
    @GetMapping
    @PreAuthorize("hasAuthority('view-movements')")
    public ResponseEntity<ApiResponse<PageResponse<MovementResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID articleId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                movementService.findAll(page, size, principal.getBranchId(), articleId)));
    }

    /**
     * Enregistre un nouveau mouvement de stock et met à jour la quantité de l'article.
     *
     * @param dto       données du mouvement (type, article, quantité)
     * @param principal utilisateur authentifié
     * @return le mouvement enregistré avec le statut HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAuthority('create-movements')")
    public ResponseEntity<ApiResponse<MovementResponseDto>> create(
            @Valid @RequestBody MovementRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Mouvement enregistré",
                        movementService.create(dto, principal.getBranchId(), principal.getId())));
    }
}

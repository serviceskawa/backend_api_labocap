package com.labo.anapath.test;

import com.labo.anapath.common.Discipline;
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
 * Contrôleur REST gérant les opérations CRUD sur les catégories d'analyses.
 *
 * <p>La consultation est accessible aux utilisateurs ayant la permission {@code view-tests},
 * tandis que la création, la modification et la suppression nécessitent {@code manage-tests}.</p>
 *
 * <p>La liste accepte un paramètre {@code discipline} qui vaut {@code PATHOLOGY} par
 * défaut : les écrans d'anatomie pathologique ne voient pas les catégories de biologie.</p>
 *
 * <p>Base URL : {@code /api/v1/category-tests}</p>
 */
@RestController
@RequestMapping("/api/v1/category-tests")
@RequiredArgsConstructor
public class CategoryTestController {

    private final CategoryTestService categoryTestService;

    /**
     * Retourne la liste paginée des catégories d'analyses de la succursale courante.
     *
     * @param page      numéro de page (0-indexé, défaut 0)
     * @param size      nombre d'éléments par page (défaut 20)
     * @param discipline discipline des catégories listées (défaut PATHOLOGY)
     * @param principal principal de sécurité de l'utilisateur connecté
     * @return page de {@link CategoryTestResponseDto}
     */
    @GetMapping
    @PreAuthorize("hasAuthority('view-tests')")
    public ResponseEntity<ApiResponse<PageResponse<CategoryTestResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "PATHOLOGY") Discipline discipline,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                categoryTestService.findAll(page, size, discipline, principal.getBranchId())));
    }

    /**
     * Crée les catégories de biologie usuelles absentes de la succursale
     * (bouton « Créer les catégories par défaut »). Idempotent.
     * Refusé (422) quand le module Biologie est désactivé.
     *
     * @param principal principal de sécurité fournissant le branchId
     * @return les catégories de biologie de la succursale
     */
    @PostMapping("/biology-defaults")
    @PreAuthorize("hasAuthority('edit-tests')")
    public ResponseEntity<ApiResponse<List<CategoryTestResponseDto>>> createBiologyDefaults(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Catégories de biologie par défaut en place",
                categoryTestService.createBiologyDefaults(principal.getBranchId())));
    }

    /**
     * Retourne une catégorie d'analyses par son identifiant unique.
     *
     * @param id identifiant UUID de la catégorie
     * @return le DTO de la catégorie trouvée
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-tests')")
    public ResponseEntity<ApiResponse<CategoryTestResponseDto>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(categoryTestService.findById(id)));
    }

    /**
     * Crée une nouvelle catégorie d'analyses dans la succursale de l'utilisateur connecté.
     *
     * @param dto       données de création validées
     * @param principal principal de sécurité fournissant le branchId
     * @return le DTO de la catégorie créée avec le statut HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAuthority('edit-tests')")
    public ResponseEntity<ApiResponse<CategoryTestResponseDto>> create(
            @Valid @RequestBody CategoryTestRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Catégorie créée", categoryTestService.create(dto, principal.getBranchId())));
    }

    /**
     * Met à jour une catégorie d'analyses existante.
     *
     * @param id  identifiant UUID de la catégorie à modifier
     * @param dto nouvelles données validées
     * @return le DTO mis à jour
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-tests')")
    public ResponseEntity<ApiResponse<CategoryTestResponseDto>> update(
            @PathVariable UUID id, @Valid @RequestBody CategoryTestRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Catégorie mise à jour", categoryTestService.update(id, dto)));
    }

    /**
     * Supprime (soft-delete) une catégorie d'analyses.
     * La suppression est refusée si des analyses y sont rattachées.
     *
     * @param id identifiant UUID de la catégorie à supprimer
     * @return réponse vide avec message de confirmation
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-tests')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        categoryTestService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Catégorie supprimée", null));
    }
}

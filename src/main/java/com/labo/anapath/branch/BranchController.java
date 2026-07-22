package com.labo.anapath.branch;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Contrôleur REST exposant les opérations CRUD sur les agences du laboratoire.
 * <p>
 * Toutes les routes sont protégées par l'autorité {@code manage-branches}.
 * Les résultats sont paginés pour éviter de charger l'intégralité des agences
 * en mémoire.
 * </p>
 */
@RestController
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;

    /**
     * Retourne la liste paginée de toutes les agences.
     *
     * @param page numéro de page (commence à 0)
     * @param size nombre d'éléments par page (défaut : 20)
     * @return page de {@link BranchResponseDto} encapsulée dans une {@link ApiResponse}
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('edit-roles','edit-users')")
    public ResponseEntity<ApiResponse<PageResponse<BranchResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(branchService.findAll(page, size)));
    }

    /**
     * Retourne une agence par son identifiant unique.
     *
     * @param id identifiant UUID de l'agence
     * @return le DTO de l'agence trouvée
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-roles')")
    public ResponseEntity<ApiResponse<BranchResponseDto>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(branchService.findById(id)));
    }

    /**
     * Crée une nouvelle agence.
     *
     * @param dto données de l'agence à créer (nom obligatoire)
     * @return l'agence créée avec le statut HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAuthority('edit-roles')")
    public ResponseEntity<ApiResponse<BranchResponseDto>> create(@Valid @RequestBody BranchRequestDto dto) {
        BranchResponseDto created = branchService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Agence créée", created));
    }

    /**
     * Met à jour les informations d'une agence existante.
     *
     * @param id  identifiant de l'agence à modifier
     * @param dto nouvelles données de l'agence
     * @return l'agence mise à jour
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-roles')")
    public ResponseEntity<ApiResponse<BranchResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody BranchRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Agence mise à jour", branchService.update(id, dto)));
    }

    /**
     * Supprime (logiquement) une agence.
     * <p>
     * La suppression est refusée si des utilisateurs sont encore rattachés à l'agence.
     * </p>
     *
     * @param id identifiant de l'agence à supprimer
     * @return réponse vide confirmant la suppression
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-roles')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        branchService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Agence supprimée", null));
    }
}

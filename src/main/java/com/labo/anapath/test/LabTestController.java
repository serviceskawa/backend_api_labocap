package com.labo.anapath.test;

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
 * Contrôleur REST gérant les opérations CRUD et la recherche sur les analyses du catalogue.
 *
 * <p>La consultation et la recherche sont accessibles aux utilisateurs ayant la permission
 * {@code view-tests}, tandis que la création, la modification et la suppression nécessitent
 * {@code manage-tests}.</p>
 *
 * <p>Base URL : {@code /api/v1/lab-tests}</p>
 */
@RestController
@RequestMapping("/api/v1/lab-tests")
@RequiredArgsConstructor
public class LabTestController {

    private final LabTestService labTestService;

    /**
     * Retourne la liste paginée des analyses de la succursale courante, avec filtres optionnels.
     *
     * @param page      numéro de page (0-indexé, défaut 0)
     * @param size      nombre d'éléments par page (défaut 20)
     * @param search    terme de recherche partielle sur le nom (optionnel)
     * @param status    statut à filtrer, ACTIF/INACTIF (optionnel)
     * @param principal principal de sécurité de l'utilisateur connecté
     * @return page de {@link LabTestResponseDto}
     */
    @GetMapping
    @PreAuthorize("hasAuthority('view-tests')")
    public ResponseEntity<ApiResponse<PageResponse<LabTestResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                labTestService.findAll(page, size, search, status, principal.getBranchId())));
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('view-tests')")
    public ResponseEntity<ApiResponse<List<LabTestResponseDto>>> findAll(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(labTestService.findAll(principal.getBranchId())));
    }

    /**
     * Recherche des analyses dont le nom contient le terme fourni (insensible à la casse).
     * Utilisé pour l'autocomplétion dans les formulaires de demande d'analyses.
     *
     * @param q         terme de recherche
     * @param principal principal de sécurité fournissant le branchId
     * @return liste des analyses correspondantes
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('view-tests')")
    public ResponseEntity<ApiResponse<List<LabTestResponseDto>>> search(
            @RequestParam String q,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(labTestService.search(q, principal.getBranchId())));
    }

    /**
     * Retourne une analyse par son identifiant unique.
     *
     * @param id identifiant UUID de l'analyse
     * @return le DTO de l'analyse trouvée
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-tests')")
    public ResponseEntity<ApiResponse<LabTestResponseDto>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(labTestService.findById(id)));
    }

    /**
     * Crée une nouvelle analyse dans la succursale de l'utilisateur connecté.
     *
     * @param dto       données de création validées
     * @param principal principal de sécurité fournissant le branchId
     * @return le DTO de l'analyse créée avec le statut HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAuthority('edit-tests')")
    public ResponseEntity<ApiResponse<LabTestResponseDto>> create(
            @Valid @RequestBody LabTestRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Analyse créée", labTestService.create(dto, principal.getBranchId())));
    }

    /**
     * Met à jour une analyse existante.
     *
     * @param id  identifiant UUID de l'analyse à modifier
     * @param dto nouvelles données validées
     * @return le DTO mis à jour
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-tests')")
    public ResponseEntity<ApiResponse<LabTestResponseDto>> update(
            @PathVariable UUID id, @Valid @RequestBody LabTestRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Analyse mise à jour", labTestService.update(id, dto)));
    }

    /**
     * Supprime (soft-delete) une analyse du catalogue.
     *
     * @param id identifiant UUID de l'analyse à supprimer
     * @return réponse vide avec message de confirmation
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-tests')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        labTestService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Analyse supprimée", null));
    }
}

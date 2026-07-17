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
 * Contrôleur REST gérant les opérations CRUD sur les unités de mesure.
 *
 * <p>La consultation est accessible aux utilisateurs ayant la permission {@code view-tests},
 * tandis que la création, la modification et la suppression nécessitent {@code manage-tests}.</p>
 *
 * <p>Base URL : {@code /api/v1/unit-measurements}</p>
 */
@RestController
@RequestMapping("/api/v1/unit-measurements")
@RequiredArgsConstructor
public class UnitMeasurementController {

    private final UnitMeasurementService unitMeasurementService;

    /**
     * Retourne la liste paginée des unités de mesure de la succursale courante.
     *
     * @param page      numéro de page (0-indexé, défaut 0)
     * @param size      nombre d'éléments par page (défaut 20)
     * @param principal principal de sécurité de l'utilisateur connecté
     * @return page de {@link UnitMeasurementResponseDto}
     */
    @GetMapping
    // Les unités servent aussi le formulaire Article (menu Stocks) : `view-articles`
    // doit suffire, sinon ce menu renvoie 403 pour un profil stock sans accès analyses.
    @PreAuthorize("hasAnyAuthority('view-tests','view-articles')")
    public ResponseEntity<ApiResponse<PageResponse<UnitMeasurementResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(unitMeasurementService.findAll(page, size, principal.getBranchId())));
    }

    @GetMapping("/all")
    // Les unités servent aussi le formulaire Article (menu Stocks) : `view-articles`
    // doit suffire, sinon ce menu renvoie 403 pour un profil stock sans accès analyses.
    @PreAuthorize("hasAnyAuthority('view-tests','view-articles')")
    public ResponseEntity<ApiResponse<List<UnitMeasurementResponseDto>>> findAll(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(unitMeasurementService.findAll(principal.getBranchId())));
    }

    /**
     * Retourne une unité de mesure par son identifiant unique.
     *
     * @param id identifiant UUID de l'unité
     * @return le DTO de l'unité trouvée
     */
    @GetMapping("/{id}")
    // Les unités servent aussi le formulaire Article (menu Stocks) : `view-articles`
    // doit suffire, sinon ce menu renvoie 403 pour un profil stock sans accès analyses.
    @PreAuthorize("hasAnyAuthority('view-tests','view-articles')")
    public ResponseEntity<ApiResponse<UnitMeasurementResponseDto>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(unitMeasurementService.findById(id)));
    }

    /**
     * Crée une nouvelle unité de mesure dans la succursale de l'utilisateur connecté.
     *
     * @param dto       données de création validées
     * @param principal principal de sécurité fournissant le branchId
     * @return le DTO de l'unité créée avec le statut HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('edit-tests','edit-articles')")
    public ResponseEntity<ApiResponse<UnitMeasurementResponseDto>> create(
            @Valid @RequestBody UnitMeasurementRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Unité créée", unitMeasurementService.create(dto, principal.getBranchId())));
    }

    /**
     * Met à jour une unité de mesure existante.
     *
     * @param id  identifiant UUID de l'unité à modifier
     * @param dto nouvelles données validées
     * @return le DTO mis à jour
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('edit-tests','edit-articles')")
    public ResponseEntity<ApiResponse<UnitMeasurementResponseDto>> update(
            @PathVariable UUID id, @Valid @RequestBody UnitMeasurementRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Unité mise à jour", unitMeasurementService.update(id, dto)));
    }

    /**
     * Supprime (soft-delete) une unité de mesure.
     * La suppression est refusée si des analyses référencent cette unité.
     *
     * @param id identifiant UUID de l'unité à supprimer
     * @return réponse vide avec message de confirmation
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('edit-tests','edit-articles')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        unitMeasurementService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Unité supprimée", null));
    }
}

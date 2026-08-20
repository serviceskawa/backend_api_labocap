package com.labo.anapath.testorder;

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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/test-order-assignments")
@RequiredArgsConstructor
public class TestOrderAssignmentController {

    private final TestOrderAssignmentService assignmentService;

    /**
     * Verse une étiquette au catalogue, indépendamment de toute affectation.
     *
     * <p>Sous la permission d'écriture : enrichir le vocabulaire d'un
     * laboratoire est un acte de composition, pas de lecture.</p>
     */
    @PostMapping("/labels")
    @PreAuthorize("hasAuthority('manage-test-order-assignments')")
    public ResponseEntity<ApiResponse<java.util.List<String>>> ajouterEtiquette(
            @RequestBody java.util.Map<String, String> corps,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                assignmentService.ajouterAuCatalogue(
                        principal.getBranchId(), corps.get("value"))));
    }

    /**
     * Le catalogue tel qu'on l'administre : identifiants et usages compris.
     *
     * <p>Distinct de {@code /labels}, qui ne sert que des chaînes aux
     * sélecteurs. Sous la permission d'écriture : cet écran n'existe que pour
     * modifier.</p>
     */
    @GetMapping("/labels/catalogue")
    @PreAuthorize("hasAuthority('manage-test-order-assignments')")
    public ResponseEntity<ApiResponse<java.util.List<EtiquetteDto>>> catalogue(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                assignmentService.catalogue(principal.getBranchId())));
    }

    /** Corrige le texte d'une étiquette, sans toucher aux affectations. */
    @PutMapping("/labels/{id}")
    @PreAuthorize("hasAuthority('manage-test-order-assignments')")
    public ResponseEntity<ApiResponse<EtiquetteDto>> renommerEtiquette(
            @PathVariable UUID id,
            @RequestBody java.util.Map<String, String> corps,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                assignmentService.renommer(
                        principal.getBranchId(), id, corps.get("value"))));
    }

    /** Retire une étiquette des propositions, sans effacer aucune trace. */
    @DeleteMapping("/labels/{id}")
    @PreAuthorize("hasAuthority('manage-test-order-assignments')")
    public ResponseEntity<ApiResponse<Void>> retirerEtiquette(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        assignmentService.retirer(principal.getBranchId(), id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Les étiquettes déjà employées par la branche, pour les reproposer.
     *
     * <p>Sous la permission de lecture des affectations : c'est le même écran
     * qui s'en sert, et le vocabulaire de marquage n'a pas de sensibilité
     * propre.</p>
     */
    @GetMapping("/labels")
    @PreAuthorize("hasAuthority('view-test-order-assignments')")
    public ResponseEntity<ApiResponse<java.util.List<String>>> etiquettes(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                assignmentService.etiquettesConnues(principal.getBranchId())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('view-test-order-assignments')")
    public ResponseEntity<ApiResponse<PageResponse<AssignmentResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                assignmentService.findAll(page, size, principal.getBranchId())));
    }

    @GetMapping("/immuno")
    @PreAuthorize("hasAuthority('view-test-order-assignments')")
    public ResponseEntity<ApiResponse<PageResponse<AssignmentResponseDto>>> findAllImmuno(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                assignmentService.findAllImmuno(page, size, principal.getBranchId())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('manage-test-order-assignments')")
    public ResponseEntity<ApiResponse<AssignmentResponseDto>> create(
            @Valid @RequestBody AssignmentRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Assignment créé", assignmentService.create(dto, principal.getBranchId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('manage-test-order-assignments')")
    public ResponseEntity<ApiResponse<AssignmentResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Assignment mis à jour", assignmentService.update(id, dto)));
    }

    @PostMapping("/{id}/details")
    @PreAuthorize("hasAuthority('manage-test-order-assignments')")
    public ResponseEntity<ApiResponse<AssignmentDetailResponseDto>> addDetail(
            @PathVariable UUID id,
            @Valid @RequestBody AssignmentDetailRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Détail ajouté", assignmentService.addDetail(id, dto)));
    }

    @GetMapping("/{id}/print")
    @PreAuthorize("hasAuthority('view-test-order-assignments')")
    public ResponseEntity<ApiResponse<AssignmentPrintDto>> getPrintData(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(assignmentService.getPrintData(id)));
    }

    @DeleteMapping("/details/{detailId}")
    @PreAuthorize("hasAuthority('manage-test-order-assignments')")
    public ResponseEntity<ApiResponse<Void>> deleteDetail(@PathVariable UUID detailId) {
        assignmentService.deleteDetail(detailId);
        return ResponseEntity.ok(ApiResponse.success("Détail supprimé", null));
    }
}

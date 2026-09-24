package com.labo.anapath.biology.results;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.time.LocalDate;
import java.util.UUID;

/**
 * Saisie des résultats de biologie.
 *
 * <p>Lecture sous {@code view-biology-results}, saisie sous {@code edit-biology-results},
 * validation technique sous {@code validate-biology-results}. Les routes répondent 404
 * quand le module Biologie est désactivé ({@code /biology-*}).</p>
 *
 * <p>Chaque écriture renvoie la feuille de saisie à jour : l'écran relit indicateurs,
 * verrous et état du compte-rendu sans second appel.</p>
 *
 * <p>Base URL : {@code /api/v1/biology-results}</p>
 */
@RestController
@RequestMapping("/api/v1/biology-results")
@RequiredArgsConstructor
public class BiologyResultController {

    private final BiologyResultService service;

    /**
     * Liste de travail : analyses des bons de biologie de la succursale, urgences
     * d'abord puis les plus anciennes.
     *
     * @param status     PENDING, ENTERED ou TECH_VALIDATED (facultatif)
     * @param categoryId catégorie d'analyse (facultatif)
     * @param from       bons créés à partir de ce jour, inclus (facultatif)
     * @param to         bons créés jusqu'à ce jour, inclus (facultatif)
     */
    @GetMapping("/worklist")
    @PreAuthorize("hasAuthority('view-biology-results')")
    public ResponseEntity<ApiResponse<PageResponse<BiologyWorklistRowDto>>> worklist(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                service.worklist(status, categoryId, from, to, page, size, principal.getBranchId())));
    }

    /**
     * @param testOrderId bon de biologie validé
     * @return feuille de saisie complète
     */
    @GetMapping("/orders/{testOrderId}")
    @PreAuthorize("hasAuthority('view-biology-results')")
    public ResponseEntity<ApiResponse<BiologyWorksheetDto>> worksheet(
            @PathVariable UUID testOrderId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(service.worksheet(testOrderId, principal.getBranchId())));
    }

    /**
     * Valeurs d'une analyse PANEL.
     *
     * @return feuille de saisie à jour
     */
    @PutMapping("/orders/{testOrderId}/analyses/{labTestId}")
    @PreAuthorize("hasAuthority('edit-biology-results')")
    public ResponseEntity<ApiResponse<BiologyWorksheetDto>> savePanel(
            @PathVariable UUID testOrderId,
            @PathVariable UUID labTestId,
            @Valid @RequestBody BiologyPanelResultsRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Résultats enregistrés",
                service.savePanel(testOrderId, labTestId, dto, principal.getId(), principal.getBranchId())));
    }

    /**
     * Résultats d'une analyse CULTURE : options, germes, antibiogrammes.
     *
     * @return feuille de saisie à jour
     */
    @PutMapping("/orders/{testOrderId}/analyses/{labTestId}/culture")
    @PreAuthorize("hasAuthority('edit-biology-results')")
    public ResponseEntity<ApiResponse<BiologyWorksheetDto>> saveCulture(
            @PathVariable UUID testOrderId,
            @PathVariable UUID labTestId,
            @Valid @RequestBody BiologyCultureResultsRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Culture enregistrée",
                service.saveCulture(testOrderId, labTestId, dto, principal.getId(), principal.getBranchId())));
    }

    /**
     * Validation technique d'une analyse (sans effet en mode ONE_STEP).
     *
     * @return feuille de saisie à jour
     */
    @PostMapping("/orders/{testOrderId}/analyses/{labTestId}/technical-validation")
    @PreAuthorize("hasAuthority('validate-biology-results')")
    public ResponseEntity<ApiResponse<BiologyWorksheetDto>> validateTechnically(
            @PathVariable UUID testOrderId,
            @PathVariable UUID labTestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Analyse validée techniquement",
                service.validateTechnically(testOrderId, labTestId, principal.getId(), principal.getBranchId())));
    }

    /**
     * Annulation de la validation technique d'une analyse.
     *
     * @return feuille de saisie à jour
     */
    @DeleteMapping("/orders/{testOrderId}/analyses/{labTestId}/technical-validation")
    @PreAuthorize("hasAuthority('validate-biology-results')")
    public ResponseEntity<ApiResponse<BiologyWorksheetDto>> cancelTechnicalValidation(
            @PathVariable UUID testOrderId,
            @PathVariable UUID labTestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Validation technique annulée",
                service.cancelTechnicalValidation(testOrderId, labTestId, principal.getId(),
                        principal.getBranchId())));
    }
}

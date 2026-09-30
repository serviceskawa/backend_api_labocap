package com.labo.anapath.biology;

import com.labo.anapath.common.dto.ApiResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Options de culture : référentiel et rattachement aux analyses CULTURE.
 *
 * <p>Lecture sous {@code view-tests}, écriture sous {@code manage-culture-options}.
 * Les routes répondent 404 quand le module Biologie est désactivé.</p>
 *
 * <p>Base URL : {@code /api/v1/culture-options}</p>
 */
@RestController
@RequestMapping("/api/v1/culture-options")
@RequiredArgsConstructor
public class CultureOptionController {

    private final CultureOptionService service;

    /**
     * @param principal utilisateur connecté (succursale courante)
     * @return options de culture de la succursale
     */
    @GetMapping
    @PreAuthorize("hasAuthority('view-tests')")
    public ResponseEntity<ApiResponse<List<CultureOptionResponseDto>>> findAll(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(service.findAll(principal.getBranchId())));
    }

    /**
     * @param dto       données de l'option
     * @param principal utilisateur connecté (succursale courante)
     * @return l'option créée (201)
     */
    @PostMapping
    @PreAuthorize("hasAuthority('manage-culture-options')")
    public ResponseEntity<ApiResponse<CultureOptionResponseDto>> create(
            @Valid @RequestBody CultureOptionRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Option de culture créée", service.create(dto, principal.getBranchId())));
    }

    /**
     * @param id        identifiant de l'option
     * @param dto       nouvelles données
     * @param principal utilisateur connecté (succursale courante)
     * @return l'option modifiée
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('manage-culture-options')")
    public ResponseEntity<ApiResponse<CultureOptionResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CultureOptionRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Option de culture mise à jour",
                service.update(id, dto, principal.getBranchId())));
    }

    /**
     * @param id        identifiant de l'option
     * @param principal utilisateur connecté (succursale courante)
     * @return réponse vide
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('manage-culture-options')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        service.delete(id, principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Option de culture supprimée", null));
    }

    /**
     * @param labTestId identifiant d'une analyse CULTURE
     * @param principal utilisateur connecté (succursale courante)
     * @return options retenues par l'analyse
     */
    @GetMapping("/by-lab-test/{labTestId}")
    @PreAuthorize("hasAuthority('view-tests')")
    public ResponseEntity<ApiResponse<List<LabTestCultureOptionResponseDto>>> findByLabTest(
            @PathVariable UUID labTestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(service.findByLabTest(labTestId, principal.getBranchId())));
    }

    /**
     * @param labTestId identifiant d'une analyse CULTURE
     * @param dto       options, dans l'ordre d'affichage
     * @param principal utilisateur connecté (succursale courante)
     * @return options retenues après l'opération
     */
    @PutMapping("/by-lab-test/{labTestId}")
    @PreAuthorize("hasAuthority('manage-culture-options')")
    public ResponseEntity<ApiResponse<List<LabTestCultureOptionResponseDto>>> setForLabTest(
            @PathVariable UUID labTestId,
            @Valid @RequestBody LabTestCultureOptionsRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Options de culture de l'analyse enregistrées",
                service.setForLabTest(labTestId, dto.getCultureOptionIds(), principal.getBranchId())));
    }
}

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
 * Référentiel des antibiotiques de l'antibiogramme.
 *
 * <p>Lecture sous {@code view-tests}, écriture sous {@code manage-antibiotics}. Les
 * routes répondent 404 quand le module Biologie est désactivé.</p>
 *
 * <p>Base URL : {@code /api/v1/antibiotics}</p>
 */
@RestController
@RequestMapping("/api/v1/antibiotics")
@RequiredArgsConstructor
public class AntibioticController {

    private final AntibioticService service;

    /**
     * @param principal utilisateur connecté (succursale courante)
     * @return antibiotiques de la succursale
     */
    @GetMapping
    @PreAuthorize("hasAuthority('view-tests')")
    public ResponseEntity<ApiResponse<List<AntibioticResponseDto>>> findAll(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(service.findAll(principal.getBranchId())));
    }

    /**
     * @param dto       données de l'antibiotique
     * @param principal utilisateur connecté (succursale courante)
     * @return l'antibiotique créé (201)
     */
    @PostMapping
    @PreAuthorize("hasAuthority('manage-antibiotics')")
    public ResponseEntity<ApiResponse<AntibioticResponseDto>> create(
            @Valid @RequestBody AntibioticRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Antibiotique créé", service.create(dto, principal.getBranchId())));
    }

    /**
     * @param id        identifiant de l'antibiotique
     * @param dto       nouvelles données
     * @param principal utilisateur connecté (succursale courante)
     * @return l'antibiotique modifié
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('manage-antibiotics')")
    public ResponseEntity<ApiResponse<AntibioticResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody AntibioticRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Antibiotique mis à jour",
                service.update(id, dto, principal.getBranchId())));
    }

    /**
     * @param id        identifiant de l'antibiotique
     * @param principal utilisateur connecté (succursale courante)
     * @return réponse vide
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('manage-antibiotics')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        service.delete(id, principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Antibiotique supprimé", null));
    }
}

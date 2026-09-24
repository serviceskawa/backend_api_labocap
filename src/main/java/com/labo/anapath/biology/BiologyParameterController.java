package com.labo.anapath.biology;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Fiche de paramètres des analyses de biologie (sections, paramètres, valeurs de référence).
 *
 * <p>Lecture sous {@code view-tests}, comme le reste du catalogue ; écriture sous
 * {@code manage-biology-parameters}. Les routes répondent 404 quand le module
 * Biologie est désactivé ({@link com.labo.anapath.common.module.ModulesWebConfig}).</p>
 *
 * <p>Base URL : {@code /api/v1/biology-parameters}</p>
 */
@RestController
@RequestMapping("/api/v1/biology-parameters")
@RequiredArgsConstructor
public class BiologyParameterController {

    private final BiologySheetService sheetService;

    /**
     * Fiche complète d'une analyse de biologie.
     *
     * @param labTestId identifiant de l'analyse
     * @param principal utilisateur connecté (succursale courante)
     * @return sections, paramètres et plages dans l'ordre d'affichage
     */
    @GetMapping("/sheet/{labTestId}")
    @PreAuthorize("hasAuthority('view-tests')")
    public ResponseEntity<ApiResponse<BiologySheetResponseDto>> getSheet(
            @PathVariable UUID labTestId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(sheetService.getSheet(labTestId, principal.getBranchId())));
    }

    /**
     * Remplace la fiche d'une analyse PANEL (voir {@link BiologySheetRequestDto}).
     *
     * @param labTestId identifiant de l'analyse
     * @param fiche     fiche complète souhaitée
     * @param principal utilisateur connecté (succursale courante)
     * @return la fiche telle qu'enregistrée
     */
    @PutMapping("/sheet/{labTestId}")
    @PreAuthorize("hasAuthority('manage-biology-parameters')")
    public ResponseEntity<ApiResponse<BiologySheetResponseDto>> saveSheet(
            @PathVariable UUID labTestId,
            @Valid @RequestBody BiologySheetRequestDto fiche,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Fiche de paramètres enregistrée",
                sheetService.saveSheet(labTestId, fiche, principal.getBranchId())));
    }
}

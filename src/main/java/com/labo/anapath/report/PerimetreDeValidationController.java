package com.labo.anapath.report;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.security.UserPrincipal;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Administration des périmètres de validation.
 *
 * <p>Gardé par {@code edit-users} et non par une permission propre : accorder à
 * quelqu'un le droit de valider des comptes-rendus est du même ordre que lui
 * attribuer un rôle, et c'est le même écran d'administration qui le fera.</p>
 */
@RestController
@RequestMapping("/api/v1/perimetres-de-validation")
@RequiredArgsConstructor
public class PerimetreDeValidationController {

    private final ServicePerimetreDeValidation service;

    /** Les types d'examen confiés à un compte. */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('view-users')")
    public ResponseEntity<ApiResponse<List<String>>> lire(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success(service.typesConfies(userId)));
    }

    /**
     * Remplace le périmètre d'un compte.
     *
     * <p>Remplacement et non ajout : l'écran montre des cases à cocher, et son
     * enregistrement vaut pour l'état entier. Une liste vide retire tout.</p>
     */
    @PutMapping("/{userId}")
    @PreAuthorize("hasAuthority('edit-users')")
    public ResponseEntity<ApiResponse<List<String>>> definir(
            @PathVariable UUID userId,
            @RequestBody PerimetreRequest requete,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<String> confies = service.definirLePerimetre(
                userId, principal.getBranchId(), requete.types(), principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Périmètre de validation enregistré", confies));
    }

    /** Les libellés des types d'examen, tels qu'on les coche. */
    public record PerimetreRequest(@NotNull List<String> types) {}
}

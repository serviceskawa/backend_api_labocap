package com.labo.anapath.discussion;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.labo.anapath.discussion.DiscussionDtos.*;

/**
 * La discussion attachée à un dossier.
 *
 * <p>Gardée par {@code view-test-orders} : participer à la conversation d'un
 * cas suppose le droit de consulter ce cas, ni plus ni moins. Le fil ne donne
 * accès à aucun contenu médical — il ne sert que ce que des humains s'y sont
 * dit.</p>
 *
 * <p>L'écriture n'exige rien de plus que la lecture, et c'est voulu : un
 * technicien doit pouvoir signaler une lame cassée sans qu'on lui ouvre le
 * droit de modifier un compte rendu.</p>
 */
@RestController
@RequestMapping("/api/v1/discussions")
@RequiredArgsConstructor
public class DiscussionController {

    private final DiscussionService service;

    /** Le fil d'un dossier, créé à la première ouverture s'il n'existe pas. */
    @GetMapping("/test-order/{testOrderId}")
    @PreAuthorize("hasAuthority('view-test-orders')")
    public ResponseEntity<ApiResponse<FilDto>> fil(
            @PathVariable UUID testOrderId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                service.fil(testOrderId, principal.getId(), principal.getBranchId())));
    }

    @PostMapping("/test-order/{testOrderId}/messages")
    @PreAuthorize("hasAuthority('view-test-orders')")
    public ResponseEntity<ApiResponse<MessageDto>> poster(
            @PathVariable UUID testOrderId,
            @RequestBody NouveauMessage nouveau,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                service.poster(testOrderId, nouveau,
                        principal.getId(), principal.getBranchId())));
    }

    /**
     * Marque le fil comme lu.
     *
     * <p>Appelé en ouvrant la discussion : la maquette veut que l'ouverture
     * fasse disparaître les badges de ce dossier, aux trois endroits où ils
     * apparaissent.</p>
     */
    @PostMapping("/test-order/{testOrderId}/lu")
    @PreAuthorize("hasAuthority('view-test-orders')")
    public ResponseEntity<ApiResponse<Void>> marquerLu(
            @PathVariable UUID testOrderId,
            @AuthenticationPrincipal UserPrincipal principal) {
        service.marquerLu(testOrderId, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Ce que la personne connectée n'a pas lu, dossier par dossier.
     *
     * <p>Le sien et rien d'autre : l'identité vient du jeton. Sert les trois
     * badges — la bannière d'accueil, la ligne de « Mes demandes », et le
     * bouton flottant de la fiche.</p>
     */
    @GetMapping("/non-lus")
    @PreAuthorize("hasAuthority('view-test-orders')")
    public ResponseEntity<ApiResponse<List<NonLusDto>>> nonLus(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(service.nonLus(principal.getId())));
    }
}

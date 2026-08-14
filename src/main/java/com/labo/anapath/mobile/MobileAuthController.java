package com.labo.anapath.mobile;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.security.UserPrincipal;
import com.labo.anapath.mobile.MobileDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Authentification et gestion des appareils de l'application mobile.
 *
 * <p>Deux chemins seulement sont ouverts sans jeton — l'enrôlement et la
 * connexion —, et tous deux exigent un secret remis hors ligne : un code
 * d'enrôlement délivré par un administrateur, puis le PIN du porteur. Le reste
 * demande une session ordinaire.</p>
 */
@RestController
@RequestMapping("/api/v1/mobile")
@RequiredArgsConstructor
public class MobileAuthController {

    private final MobileAuthService mobileAuthService;

    /**
     * Délivre un code d'enrôlement pour un utilisateur.
     *
     * <p>Le code en clair n'est renvoyé qu'ici, une seule fois : la base n'en
     * garde que l'empreinte. L'administrateur doit le transmettre aussitôt.</p>
     */
    @PostMapping("/enrollment-codes")
    @PreAuthorize("hasAuthority('edit-users')")
    public ResponseEntity<ApiResponse<EnrollmentCodeResponse>> creerCode(
            @Valid @RequestBody EnrollmentCodeRequest requete,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Code d'enrôlement créé",
                mobileAuthService.creerCodeEnrolement(
                        requete.userId(), principal.getId(), principal.getBranchId())));
    }

    /** Échange un code d'enrôlement contre l'identité d'un appareil. Public. */
    @PostMapping("/enroll")
    public ResponseEntity<ApiResponse<EnrollResponse>> enroler(@Valid @RequestBody EnrollRequest requete) {
        return ResponseEntity.ok(ApiResponse.success("Appareil enrôlé", mobileAuthService.enroler(requete)));
    }

    /** Ouvre une session depuis un appareil enrôlé. Public. */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<MobileLoginResponse>> connecter(
            @Valid @RequestBody MobileLoginRequest requete) {
        return ResponseEntity.ok(ApiResponse.success("Connexion réussie", mobileAuthService.connecter(requete)));
    }

    /**
     * Pose ou remplace son propre code PIN.
     *
     * <p>Réservé au porteur : un PIN qu'un tiers pourrait fixer cesserait d'être
     * une preuve de connaissance, et l'imputation des signatures avec lui.</p>
     */
    @PostMapping("/pin")
    public ResponseEntity<ApiResponse<Void>> definirPin(
            @Valid @RequestBody PinRequest requete,
            @AuthenticationPrincipal UserPrincipal principal) {
        mobileAuthService.definirPin(principal.getId(), requete.pin());
        return ResponseEntity.ok(ApiResponse.success("Code PIN enregistré", null));
    }

    @GetMapping("/devices")
    @PreAuthorize("hasAuthority('view-users')")
    public ResponseEntity<ApiResponse<List<DeviceResponse>>> lister(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                mobileAuthService.listerAppareils(principal.getBranchId())));
    }

    @PostMapping("/devices/{id}/revoke")
    @PreAuthorize("hasAuthority('edit-users')")
    public ResponseEntity<ApiResponse<Void>> revoquer(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        mobileAuthService.revoquer(id, principal.getId(), principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Appareil révoqué", null));
    }
}

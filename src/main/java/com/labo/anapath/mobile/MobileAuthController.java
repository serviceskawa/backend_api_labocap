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
     * Ouvre l'accès mobile à un utilisateur, en un seul geste.
     *
     * <p>Accorde le droit, engendre son code PIN et son code d'enrôlement, et
     * les renvoie en clair — <strong>la seule et unique fois</strong>. La base
     * n'en garde que les empreintes : les retrouver plus tard est impossible,
     * il faudra rouvrir l'accès pour en régénérer.</p>
     */
    @PostMapping("/access/{userId}")
    @PreAuthorize("hasAuthority('edit-users')")
    public ResponseEntity<ApiResponse<AccesMobileResponse>> ouvrirAcces(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                "Accès mobile ouvert",
                mobileAuthService.ouvrirAcces(userId, principal.getId(), principal.getBranchId())));
    }

    /** Ferme l'accès : retire le droit, efface le PIN, révoque les appareils. */
    @DeleteMapping("/access/{userId}")
    @PreAuthorize("hasAuthority('edit-users')")
    public ResponseEntity<ApiResponse<Void>> fermerAcces(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        mobileAuthService.fermerAcces(userId, principal.getId(), principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Accès mobile fermé", null));
    }

    /** État de l'accès d'un utilisateur : droit, PIN posé, appareils enrôlés. */
    @GetMapping("/access/{userId}")
    @PreAuthorize("hasAuthority('view-users')")
    public ResponseEntity<ApiResponse<EtatAccesResponse>> etatAcces(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                mobileAuthService.etatAcces(userId, principal.getBranchId())));
    }

    /**
     * Délivre un code d'enrôlement pour un utilisateur.
     *
     * <p>Le code en clair n'est renvoyé qu'ici : la base n'en garde que
     * l'empreinte. Il reste valable jusqu'à sa révocation et sert autant de
     * fois qu'il le faut, mais en délivrer un nouveau éteint le précédent.</p>
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

    /**
     * Éteint le code d'enrôlement d'un utilisateur.
     *
     * <p>Les appareils déjà enrôlés continuent de fonctionner : c'est la porte
     * qu'on ferme, pas les clés déjà remises. Pour celles-là,
     * {@code /devices/{id}/revoke} ou la fermeture de l'accès.</p>
     */
    @DeleteMapping("/enrollment-codes/{userId}")
    @PreAuthorize("hasAuthority('edit-users')")
    public ResponseEntity<ApiResponse<Void>> revoquerCode(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserPrincipal principal) {
        mobileAuthService.revoquerCodeEnrolement(
                userId, principal.getId(), principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Code d'enrôlement révoqué", null));
    }

    /** Échange un code d'enrôlement contre l'identité d'un appareil. Public. */
    @PostMapping("/enroll")
    public ResponseEntity<ApiResponse<EnrollResponse>> enroler(@Valid @RequestBody EnrollRequest requete) {
        return ResponseEntity.ok(ApiResponse.success("Appareil enrôlé", mobileAuthService.enroler(requete)));
    }

    /**
     * Renouvelle la session sans redemander le PIN. Public.
     *
     * <p>Le jeton est présenté dans le corps et non par cookie : c'est la même
     * rotation que le web — jeton consommé mis en liste noire — mais l'application
     * range le sien dans le trousseau du système et doit pouvoir le fournir.</p>
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<MobileLoginResponse>> rafraichir(
            @Valid @RequestBody MobileRefreshRequest requete) {
        return ResponseEntity.ok(ApiResponse.success("Session renouvelée",
                mobileAuthService.rafraichir(requete)));
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

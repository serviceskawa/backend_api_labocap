package com.labo.anapath.user;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.security.UserPrincipal;
import com.labo.anapath.role.PermissionResponseDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
 * Contrôleur REST gérant les opérations CRUD sur les utilisateurs.
 *
 * <p>Toutes les routes sont protégées par la permission {@code manage-users}.
 * Les données sont filtrées par la succursale de l'utilisateur connecté.</p>
 *
 * <p>Base URL : {@code /api/v1/users}</p>
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    /**
     * Retourne la liste paginée des utilisateurs de la succursale courante.
     *
     * @param page      numéro de page (0-indexé, défaut 0)
     * @param size      nombre d'éléments par page (défaut 20)
     * @param principal principal de sécurité de l'utilisateur connecté
     * @return page de {@link UserResponseDto}
     */
    /**
     * Les signataires possibles d'un compte rendu — les porteurs du rôle « docteur ».
     *
     * <p>Surface volontairement minimale : identifiant, nom, état du compte. Elle
     * existe parce que {@code GET /users} exige {@code edit-users}, permission
     * qu'aucun médecin ne possède — leur rôle ne porte que des droits sur les
     * comptes rendus. Le sélecteur de signataire recevait donc un 403 silencieux
     * et s'affichait vide, pour tous les médecins.</p>
     *
     * <p>Élargir {@code /users} aurait donné aux médecins le droit de créer et de
     * supprimer des comptes ; on ouvre plutôt cette liste-ci, sous la permission
     * qu'ils ont déjà pour éditer un compte rendu.</p>
     */
    @GetMapping("/signataires")
    @PreAuthorize("hasAuthority('edit-reports')")
    public ResponseEntity<ApiResponse<List<SignataireDto>>> signataires() {
        return ResponseEntity.ok(ApiResponse.success(
                userRepository.findSignataires().stream().map(SignataireDto::de).toList()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('edit-users')")
    public ResponseEntity<ApiResponse<PageResponse<UserResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        PageResponse<UserResponseDto> result = userService.findAll(page, size, principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * Retourne un utilisateur par son identifiant unique.
     *
     * @param id        identifiant UUID de l'utilisateur
     * @param principal principal de sécurité de l'utilisateur connecté
     * @return le DTO de l'utilisateur trouvé
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-users')")
    public ResponseEntity<ApiResponse<UserResponseDto>> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(userService.findById(id, principal.getBranchId())));
    }

    /**
     * Crée un nouvel utilisateur dans la succursale de l'utilisateur connecté.
     *
     * @param dto       données de création validées
     * @param principal principal de sécurité fournissant le branchId
     * @return le DTO de l'utilisateur créé avec le statut HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAuthority('edit-users')")
    public ResponseEntity<ApiResponse<UserResponseDto>> create(
            @Valid @RequestBody UserRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserResponseDto created = userService.create(dto, principal.getBranchId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Utilisateur créé avec succès", created));
    }

    /**
     * Met à jour les informations d'un utilisateur existant.
     *
     * @param id        identifiant UUID de l'utilisateur à modifier
     * @param dto       nouvelles données validées
     * @param principal principal de sécurité fournissant le branchId
     * @return le DTO mis à jour
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-users')")
    public ResponseEntity<ApiResponse<UserResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody UserRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Utilisateur mis à jour", userService.update(id, dto, principal.getBranchId())));
    }

    /**
     * Supprime (soft-delete) un utilisateur par son identifiant.
     *
     * @param id        identifiant UUID de l'utilisateur à supprimer
     * @param principal principal de sécurité fournissant le branchId
     * @return réponse vide avec message de confirmation
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-users')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        userService.delete(id, principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Utilisateur supprimé", null));
    }

    /**
     * Bascule le statut actif/inactif d'un utilisateur.
     * La désactivation déconnecte également l'utilisateur et désactive le 2FA.
     *
     * @param id        identifiant UUID de l'utilisateur
     * @param principal principal de sécurité fournissant le branchId
     * @return réponse vide avec message de confirmation
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('edit-users')")
    public ResponseEntity<ApiResponse<Void>> toggleStatus(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        userService.toggleStatus(id, principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Statut mis à jour", null));
    }

    /**
     * Met à jour le mot de passe d'un utilisateur après vérification de l'ancien.
     *
     * @param id        identifiant UUID de l'utilisateur
     * @param request   objet contenant l'ancien et le nouveau mot de passe
     * @param principal principal de sécurité fournissant le branchId
     * @return réponse vide avec message de confirmation
     */
    @PatchMapping("/{id}/password")
    @PreAuthorize("hasAuthority('edit-users')")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePasswordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        userService.updatePassword(id, request, principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Mot de passe mis à jour", null));
    }

    // ---------------------------------------------------------------------
    // Routes « self-service » : l'utilisateur agit sur son propre compte.
    // Elles n'exigent que d'être authentifié — les routes /{id} ci-dessus
    // restent réservées aux détenteurs de la permission « edit-users ».
    // ---------------------------------------------------------------------

    /**
     * Met à jour les informations personnelles de l'utilisateur connecté.
     *
     * @param request   nouvelles informations personnelles
     * @param principal principal de sécurité de l'utilisateur connecté
     * @return le DTO mis à jour
     */
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponseDto>> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserResponseDto updated = userService.updateMyProfile(
                principal.getId(), request, principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Profil mis à jour", updated));
    }

    /**
     * Change l'adresse e-mail de connexion de l'utilisateur connecté.
     *
     * <p>Le mot de passe actuel est exigé dans le corps de la requête.</p>
     *
     * @param request   nouvelle adresse et mot de passe de confirmation
     * @param principal principal de sécurité de l'utilisateur connecté
     * @return le DTO mis à jour
     */
    @PatchMapping("/me/email")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<UserResponseDto>> updateMyEmail(
            @Valid @RequestBody UpdateEmailRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UserResponseDto updated = userService.updateMyEmail(
                principal.getId(), request, principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Adresse e-mail mise à jour", updated));
    }

    /**
     * Change le mot de passe de l'utilisateur connecté, après vérification de l'ancien.
     *
     * @param request   ancien et nouveau mot de passe
     * @param principal principal de sécurité de l'utilisateur connecté
     * @return réponse vide avec message de confirmation
     */
    @PatchMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> updateMyPassword(
            @Valid @RequestBody UpdatePasswordRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        userService.updatePassword(principal.getId(), request, principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Mot de passe mis à jour", null));
    }

    /**
     * Retourne les permissions directement assignées à un utilisateur.
     *
     * @param id        identifiant UUID de l'utilisateur
     * @param principal principal de sécurité fournissant le branchId
     * @return liste des DTOs de permissions directes
     */
    @GetMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('edit-users')")
    public ResponseEntity<ApiResponse<List<PermissionResponseDto>>> getUserPermissions(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                userService.getUserPermissions(id, principal.getBranchId())));
    }

    /**
     * Remplace toutes les permissions directes d'un utilisateur.
     *
     * @param id            identifiant UUID de l'utilisateur
     * @param permissionIds liste des UUIDs de permissions à assigner
     * @param principal     principal de sécurité fournissant le branchId
     * @return réponse vide avec message de confirmation
     */
    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('edit-users')")
    public ResponseEntity<ApiResponse<Void>> setUserPermissions(
            @PathVariable UUID id,
            @RequestBody List<UUID> permissionIds,
            @AuthenticationPrincipal UserPrincipal principal) {
        userService.setUserPermissions(id, permissionIds, principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Permissions mises à jour", null));
    }
}

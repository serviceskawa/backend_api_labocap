package com.labo.anapath.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Requête de mise à jour des informations personnelles de l'utilisateur courant.
 *
 * <p>Volontairement restreinte aux champs qu'un utilisateur peut modifier lui-même :
 * ni {@code roleIds}, ni {@code isActive}, ni {@code email} n'y figurent. L'e-mail
 * passe par {@link UpdateEmailRequest} et le mot de passe par
 * {@link UpdatePasswordRequest}, tous deux protégés par le mot de passe actuel.</p>
 */
@Getter
@Setter
public class UpdateProfileRequest {

    /** Prénom de l'utilisateur (obligatoire). */
    @NotBlank(message = "Le prénom est obligatoire")
    private String firstname;

    /** Nom de famille de l'utilisateur (obligatoire). */
    @NotBlank(message = "Le nom est obligatoire")
    private String lastname;

    /** Numéro de téléphone (optionnel). */
    private String phone;

    /** Signature manuscrite encodée en data-URL base64 (optionnel). */
    private String signature;
}

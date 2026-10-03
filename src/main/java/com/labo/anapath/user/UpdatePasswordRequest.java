package com.labo.anapath.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Requête de changement de mot de passe d'un utilisateur.
 *
 * <p>L'ancien mot de passe est exigé pour confirmer l'identité du demandeur
 * avant d'autoriser la mise à jour.</p>
 */
@Getter
@Setter
public class UpdatePasswordRequest {

    /** Mot de passe actuel permettant de vérifier l'identité du demandeur. */
    @NotBlank(message = "L'ancien mot de passe est obligatoire")
    private String currentPassword;

    /** Nouveau mot de passe en clair (minimum 8 caractères). Sera haché en BCrypt. */
    @NotBlank(message = "Le nouveau mot de passe est obligatoire")
    @Size(min = 12, message = "Le mot de passe doit contenir au moins 12 caractères")
    private String newPassword;
}

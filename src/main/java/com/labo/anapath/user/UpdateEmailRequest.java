package com.labo.anapath.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Requête de changement de l'adresse e-mail de connexion de l'utilisateur courant.
 *
 * <p>Le mot de passe actuel est exigé : l'e-mail étant l'identifiant de connexion,
 * le modifier depuis une session volée suffirait sinon à détourner le compte.</p>
 */
@Getter
@Setter
public class UpdateEmailRequest {

    /** Nouvelle adresse e-mail de connexion. */
    @NotBlank(message = "La nouvelle adresse e-mail est obligatoire")
    @Email(message = "L'email doit être valide")
    private String newEmail;

    /** Mot de passe actuel, permettant de confirmer l'identité du demandeur. */
    @NotBlank(message = "Le mot de passe actuel est obligatoire")
    private String currentPassword;
}

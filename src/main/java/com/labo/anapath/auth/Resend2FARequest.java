package com.labo.anapath.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de requête pour le renvoi d'un code OTP de second facteur.
 * <p>
 * Reçu sur {@code POST /api/v1/auth/resend-2fa}.
 * </p>
 */
@Getter
@Setter
public class Resend2FARequest {

    /** Adresse e-mail du compte pour lequel renvoyer le code OTP. */
    /**
     * Facultatif depuis le lot 11 : le navigateur ne garde plus l'adresse en
     * clair, le serveur la retrouve par le cookie {@code pending_2fa} (jeton
     * temporaire de la connexion en cours). L'adresse reste acceptée pour les
     * clients qui l'envoient.
     */
    @Email(message = "L'email doit être valide")
    private String email;

    /** Jeton temporaire de la connexion en cours, lu du cookie HttpOnly par le contrôleur. */
    private String tempToken;
}

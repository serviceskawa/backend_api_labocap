package com.labo.anapath.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de requête pour la réinitialisation effective du mot de passe.
 * <p>
 * Reçu sur {@code POST /api/v1/auth/reset-password}.
 * </p>
 */
@Getter
@Setter
public class ResetPasswordRequest {

    /** Token de réinitialisation reçu par l'utilisateur. */
    @NotBlank(message = "Le token est obligatoire")
    private String token;

    /** Nouveau mot de passe. */
    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 12, message = "Le mot de passe doit contenir au moins 12 caractères")
    private String password;

    /** Confirmation du nouveau mot de passe, doit être identique à {@code password}. */
    @NotBlank(message = "La confirmation du mot de passe est obligatoire")
    private String passwordConfirmation;
}

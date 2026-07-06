package com.labo.anapath.doctor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de requête pour la création ou la mise à jour d'un hôpital.
 * <p>
 * Seul le nom est obligatoire (conforme à la validation Laravel). Le téléphone,
 * l'adresse, l'email et la commission sont optionnels.
 * </p>
 */
@Getter
@Setter
public class HospitalRequestDto {

    /** Nom officiel de l'hôpital ou de la structure sanitaire (obligatoire). */
    @NotBlank(message = "Le nom de l'hôpital est obligatoire")
    private String name;

    /** Numéro de téléphone principal de l'établissement (optionnel, comme Laravel). */
    private String telephone;

    /** Adresse physique de l'établissement (optionnel). */
    private String adresse;

    /** Adresse e-mail de contact (optionnel, doit être valide si fournie). */
    @Email(message = "L'email doit être valide")
    private String email;

    /** Taux de commission accordé à l'établissement (optionnel, en pourcentage). */
    private Double commission;
}

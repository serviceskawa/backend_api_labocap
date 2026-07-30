package com.labo.anapath.doctor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de requête pour la création ou la mise à jour d'un médecin prescripteur.
 * <p>
 * Seul le nom est obligatoire. Le taux de commission est optionnel et ne s'applique
 * que si un accord commercial existe avec le médecin.
 * </p>
 */
@Getter
@Setter
public class DoctorRequestDto {

    /** Nom complet du médecin (obligatoire). */
    @NotBlank(message = "Le nom du médecin est obligatoire")
    private String name;

    /** Numéro de téléphone du médecin (optionnel). */
    private String telephone;

    /** Adresse e-mail professionnelle (optionnel, doit être valide si fourni). */
    @Email(message = "L'email doit être valide")
    private String email;

    /** Spécialité ou fonction du médecin (ex. : "Chirurgien", "Généraliste"). */
    private String role;

    /** Taux de commission accordé au médecin prescripteur (optionnel, en pourcentage). */
    @PositiveOrZero(message = "La commission ne peut pas être négative")
    private Double commission;
}

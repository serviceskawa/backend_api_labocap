package com.labo.anapath.client;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import jakarta.validation.constraints.Pattern;

/**
 * DTO de requête pour la création ou la mise à jour d'un client institutionnel.
 * <p>
 * Le nom est le seul champ obligatoire. L'IFU (Identifiant Fiscal Unique)
 * est optionnel mais doit être unique toutes agences confondues s'il est fourni.
 * </p>
 */
@Getter
@Setter
public class ClientRequestDto {

    /** Raison sociale ou nom du client (obligatoire). */
    @NotBlank(message = "Le nom du client est obligatoire")
    private String name;

    /**
     * Numéro IFU (Identifiant Fiscal Unique) du client — optionnel, mais unique
     * globalement. Format imposé : exactement 13 chiffres.
     */
    @Pattern(regexp = "^$|^[0-9]{13}$",
             message = "Le numéro IFU doit contenir exactement 13 chiffres (ex. 1234567890123)")
    private String ifu;

    /** Adresse physique du client (optionnel). */
    private String adress;

    /** Téléphone de contact — obligatoire, 8 à 15 chiffres (ex. 0197000000). */
    @NotBlank(message = "Le contact est obligatoire")
    @Pattern(regexp = "^[0-9]{8,15}$",
             message = "Le contact doit contenir entre 8 et 15 chiffres (ex. 0197000000)")
    private String contact;
}

package com.labo.anapath.test;

import com.labo.anapath.common.Discipline;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de requête pour la création et la mise à jour d'une catégorie d'analyses.
 */
@Getter
@Setter
public class CategoryTestRequestDto {

    /** Nom de la catégorie (obligatoire, ex. : "Hématologie"). */
    @NotBlank(message = "Le nom de la catégorie est obligatoire")
    private String name;

    /** Code court optionnel identifiant la catégorie (ex. : "HEM"). */
    private String code;

    /**
     * Discipline de la catégorie ; {@code null} vaut {@link Discipline#PATHOLOGY}.
     * Prise en compte à la création seulement : une modification qui la change est refusée.
     */
    private Discipline discipline;
}

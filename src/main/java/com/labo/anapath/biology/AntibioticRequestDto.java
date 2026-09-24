package com.labo.anapath.biology;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de requête pour la création et la mise à jour d'un antibiotique.
 */
@Getter
@Setter
public class AntibioticRequestDto {

    /** Dénomination commune (obligatoire, unique dans la succursale). */
    @NotBlank(message = "Le nom de l'antibiotique est obligatoire")
    @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
    private String name;

    /** Nom commercial usuel. */
    @Size(max = 150, message = "Le nom commercial ne doit pas dépasser 150 caractères")
    private String commercialName;

    /** Famille d'antibiotiques. */
    @Size(max = 100, message = "La famille ne doit pas dépasser 100 caractères")
    private String family;

    /** Code court, unique dans la succursale s'il est renseigné. */
    @Size(max = 50, message = "Le code ne doit pas dépasser 50 caractères")
    private String code;

    /** Rang dans l'antibiogramme (0 par défaut). */
    @PositiveOrZero(message = "La position doit être positive")
    private Integer position;
}

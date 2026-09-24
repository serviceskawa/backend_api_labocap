package com.labo.anapath.biology;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * DTO de requête pour la création et la mise à jour d'une option de culture.
 */
@Getter
@Setter
public class CultureOptionRequestDto {

    /** Libellé de la rubrique (obligatoire, unique dans la succursale). */
    @NotBlank(message = "Le nom de l'option de culture est obligatoire")
    @Size(max = 150, message = "Le nom ne doit pas dépasser 150 caractères")
    private String name;

    /** Valeurs proposées ; vide = saisie libre. Rognées et dédoublonnées. */
    private List<String> choices;

    /** Rang d'affichage par défaut (0 par défaut). */
    @PositiveOrZero(message = "La position doit être positive")
    private Integer position;
}

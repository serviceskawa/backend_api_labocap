package com.labo.anapath.biology;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Options de culture retenues par une analyse CULTURE, dans l'ordre d'affichage.
 * La liste remplace la précédente ; l'ordre fait foi pour les positions.
 */
@Getter
@Setter
public class LabTestCultureOptionsRequestDto {

    /** Identifiants des options de culture, dans l'ordre d'affichage. */
    @NotNull(message = "La liste des options de culture est obligatoire")
    private List<UUID> cultureOptionIds = new ArrayList<>();
}

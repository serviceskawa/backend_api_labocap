package com.labo.anapath.biology;

import java.util.List;
import java.util.UUID;

/**
 * Option de culture retenue par une analyse.
 *
 * @param id              identifiant du lien analyse ↔ option
 * @param cultureOptionId identifiant de l'option de culture
 * @param name            libellé de l'option
 * @param choices         valeurs proposées
 * @param position        rang d'affichage pour cette analyse
 */
public record LabTestCultureOptionResponseDto(UUID id, UUID cultureOptionId, String name,
                                              List<String> choices, int position) {

    /** Construit le DTO depuis l'entité. */
    static LabTestCultureOptionResponseDto of(LabTestCultureOption l) {
        BiologyCultureOption o = l.getCultureOption();
        return new LabTestCultureOptionResponseDto(l.getId(), o.getId(), o.getName(),
                o.getChoices() == null ? List.of() : o.getChoices(), l.getPosition());
    }
}

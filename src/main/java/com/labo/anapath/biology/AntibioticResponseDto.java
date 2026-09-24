package com.labo.anapath.biology;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Antibiotique exposé par l'API.
 *
 * @param id             identifiant
 * @param name           dénomination commune
 * @param commercialName nom commercial
 * @param family         famille
 * @param code           code court
 * @param position       rang dans l'antibiogramme
 * @param createdAt      date de création
 */
public record AntibioticResponseDto(
        UUID id,
        String name,
        String commercialName,
        String family,
        String code,
        int position,
        LocalDateTime createdAt
) {

    /** Construit le DTO depuis l'entité. */
    static AntibioticResponseDto of(Antibiotic a) {
        return new AntibioticResponseDto(a.getId(), a.getName(), a.getCommercialName(), a.getFamily(),
                a.getCode(), a.getPosition(), a.getCreatedAt());
    }
}

package com.labo.anapath.biology;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Option de culture exposée par l'API.
 *
 * @param id        identifiant
 * @param name      libellé de la rubrique
 * @param choices   valeurs proposées
 * @param position  rang d'affichage par défaut
 * @param createdAt date de création
 */
public record CultureOptionResponseDto(UUID id, String name, List<String> choices, int position,
                                       LocalDateTime createdAt) {

    /** Construit le DTO depuis l'entité. */
    static CultureOptionResponseDto of(BiologyCultureOption o) {
        return new CultureOptionResponseDto(o.getId(), o.getName(),
                o.getChoices() == null ? List.of() : o.getChoices(), o.getPosition(), o.getCreatedAt());
    }
}

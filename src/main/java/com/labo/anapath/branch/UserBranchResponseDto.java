package com.labo.anapath.branch;

import java.util.UUID;

/**
 * DTO d'une branche accessible par l'utilisateur connecté, destiné à l'écran de
 * sélection de branche (analogue de la page {@code select-branch} de Laravel).
 * <p>
 * Reprend les champs affichés par la carte de sélection : nom, code, localisation,
 * et l'indicateur {@code isDefault} qui pilote le badge « Par défaut » et
 * l'auto-sélection côté front.
 * </p>
 *
 * @param id        identifiant de la branche
 * @param name      nom de la branche
 * @param code      code interne de la branche
 * @param location  localisation géographique
 * @param isDefault indique si la ligne pivot {@code branch_user} est marquée par défaut
 */
public record UserBranchResponseDto(
        UUID id,
        String name,
        String code,
        String location,
        boolean isDefault
) {}

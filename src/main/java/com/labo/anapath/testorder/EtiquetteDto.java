package com.labo.anapath.testorder;

import java.util.UUID;

/**
 * Une étiquette du catalogue, telle qu'on l'administre.
 *
 * <p>Distincte de la simple chaîne servie aux sélecteurs : administrer suppose
 * de désigner une ligne précise — donc son identifiant — et de savoir ce qu'on
 * casse en y touchant, d'où le nombre d'affectations qui la portent déjà.</p>
 *
 * @param id       l'identifiant de l'étiquette
 * @param value    le texte porté sur le contenant
 * @param usages   le nombre de demandes déjà étiquetées ainsi
 */
public record EtiquetteDto(UUID id, String value, long usages) {
}

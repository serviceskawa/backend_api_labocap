package com.labo.anapath.testorder;

import java.util.List;

/**
 * Ce qu'on corrige sur une demande déjà affectée.
 *
 * <p>Distinct de {@link AssignmentDetailRequestDto}, qui sert à l'ajout et
 * exige la demande concernée. Ici la ligne est déjà désignée par son
 * identifiant : redemander le bon d'examen serait redondant, et surtout
 * permettrait d'en envoyer un autre — ce qui ferait d'une correction un
 * déplacement silencieux.</p>
 *
 * @param labels les étiquettes telles qu'elles doivent être désormais. La liste
 *               remplace, elle ne complète pas : corriger veut dire « voici ce
 *               qui est vrai maintenant », et ajouter « Immuno payé » sans
 *               retirer « Immuno non payé » laisserait les deux sur le
 *               contenant.
 * @param note   la note de la demande. Nulle, elle n'est pas touchée — sans
 *               quoi corriger une étiquette effacerait une consigne qu'on
 *               n'avait pas l'intention de retirer. Vide, elle s'efface.
 */
public record CorrectionDetailDto(List<String> labels, String note) {
}

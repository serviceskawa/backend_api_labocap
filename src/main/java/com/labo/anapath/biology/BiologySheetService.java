package com.labo.anapath.biology;

import java.util.UUID;

/**
 * Fiche de paramètres d'une analyse de biologie de nature PANEL : sections,
 * paramètres et valeurs de référence.
 */
public interface BiologySheetService {

    /**
     * Retourne la fiche complète d'une analyse, dans l'ordre d'affichage.
     *
     * @param labTestId identifiant de l'analyse
     * @param branchId  succursale courante
     * @return la fiche (vide si l'analyse n'a encore aucun paramètre)
     * @throws com.labo.anapath.common.exception.ResourceNotFoundException si l'analyse n'existe pas dans la succursale
     * @throws com.labo.anapath.common.exception.BusinessException         si ce n'est pas une analyse de biologie
     */
    BiologySheetResponseDto getSheet(UUID labTestId, UUID branchId);

    /**
     * Remplace la fiche d'une analyse PANEL : mise à jour par identifiant, création des
     * éléments sans identifiant, suppression logique des éléments absents.
     *
     * @param labTestId identifiant de l'analyse
     * @param fiche     fiche complète souhaitée
     * @param branchId  succursale courante
     * @return la fiche telle qu'enregistrée
     * @throws com.labo.anapath.common.exception.BusinessException si l'analyse n'est pas BIOLOGY/PANEL,
     *         si la fiche est incohérente (voir {@link BiologySheetValidator}) ou si un identifiant
     *         désigne un élément d'une autre analyse
     */
    BiologySheetResponseDto saveSheet(UUID labTestId, BiologySheetRequestDto fiche, UUID branchId);
}

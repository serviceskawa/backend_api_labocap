package com.labo.anapath.biology;

import com.labo.anapath.common.Discipline;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.test.LabTest;
import com.labo.anapath.test.LabTestRepository;

import java.util.UUID;

/**
 * Chargement d'une analyse de biologie de la succursale courante, avec contrôle de
 * sa nature : une fiche de paramètres ne se greffe que sur une analyse PANEL, des
 * options de culture que sur une analyse CULTURE.
 */
final class AnalysesDeBiologie {

    private AnalysesDeBiologie() {
    }

    /**
     * @param repository repository des analyses
     * @param labTestId  identifiant de l'analyse
     * @param branchId   succursale courante
     * @param nature     nature exigée, ou {@code null} pour toute analyse de biologie
     * @return l'analyse
     * @throws ResourceNotFoundException si l'analyse n'existe pas dans la succursale
     * @throws BusinessException         si ce n'est pas une analyse de biologie de la nature exigée
     */
    static LabTest charger(LabTestRepository repository, UUID labTestId, UUID branchId, BiologyKind nature) {
        LabTest analyse = repository.findByIdAndBranchId(labTestId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Analyse", labTestId));
        if (analyse.getDiscipline() != Discipline.BIOLOGY) {
            throw new BusinessException("L'analyse « " + analyse.getName() + " » n'est pas une analyse de biologie.");
        }
        if (nature != null && analyse.getBiologyKind() != nature) {
            throw new BusinessException("L'analyse « " + analyse.getName() + " » n'est pas de nature "
                    + nature + (nature == BiologyKind.PANEL
                    ? " : seule une analyse à paramètres porte une fiche."
                    : " : seule une culture retient des options de culture."));
        }
        return analyse;
    }
}

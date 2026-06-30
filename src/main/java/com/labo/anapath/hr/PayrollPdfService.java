package com.labo.anapath.hr;

import java.util.UUID;

/**
 * Service de génération du PDF d'une fiche de paie.
 */
public interface PayrollPdfService {

    /**
     * Génère le PDF d'une fiche de paie pour un employé donné.
     *
     * @param employeeId identifiant de l'employé propriétaire de la fiche
     * @param payrollId  identifiant de la fiche de paie
     * @return le contenu du PDF
     */
    byte[] generatePdf(UUID employeeId, UUID payrollId);
}

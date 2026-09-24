package com.labo.anapath.biology.report;

import java.util.UUID;

/**
 * PDF du compte-rendu de biologie ({@code templates/pdf/biologie.html}).
 */
public interface BiologyPdfService {

    /**
     * Rend le compte-rendu de biologie et journalise l'impression (« Imprimer »).
     *
     * <p>Un compte-rendu non validé ne s'imprime que si {@code bio_print_provisional}
     * le permet, sous la mention « RÉSULTATS PROVISOIRES » et sans signature ;
     * sinon 422.</p>
     *
     * @param reportId compte-rendu de biologie
     * @param userId   auteur de l'impression
     * @param branchId succursale de l'appelant, ou {@code null} sans contrôle — le
     *                 cas de {@code GET /reports/{id}/pdf}, qui n'en fait pas
     *                 pour l'anatomie pathologique
     */
    byte[] generatePdf(UUID reportId, UUID userId, UUID branchId);
}

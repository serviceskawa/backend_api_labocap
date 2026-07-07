package com.labo.anapath.finance;

import java.util.UUID;

/** Génération du PDF d'une facture (réplique Laravel {@code invoices/print}). */
public interface InvoicePdfService {

    /**
     * Génère le PDF imprimable d'une facture.
     *
     * @param invoiceId identifiant de la facture
     * @param branchId  identifiant de la branche (isolation multi-tenant)
     * @return le document PDF sous forme d'octets
     */
    byte[] generatePdf(UUID invoiceId, UUID branchId);
}

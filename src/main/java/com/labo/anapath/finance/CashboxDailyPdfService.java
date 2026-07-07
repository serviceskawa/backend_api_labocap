package com.labo.anapath.finance;

import java.util.UUID;

/** Génération du PDF de clôture de caisse journalière (réplique Laravel {@code cashbox_daily/print}). */
public interface CashboxDailyPdfService {

    /**
     * Génère le PDF récapitulatif d'une journée de caisse.
     *
     * @param dailyId  identifiant de la journée de caisse
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @return le document PDF sous forme d'octets
     */
    byte[] generatePdf(UUID dailyId, UUID branchId);
}

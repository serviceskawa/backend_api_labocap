package com.labo.anapath.finance;

import java.util.UUID;

/** Normalisation fiscale des factures par la passerelle FluidInvoice. */
public interface FluidInvoiceService {

    /**
     * Normalise une facture auprès de la DGI et enregistre le lien du document.
     *
     * <p>Une facture de vente part sur {@code POST /v1/invoices}, un avoir sur
     * {@code POST /v1/invoices/credit-note} avec la référence de son originale.</p>
     */
    InvoiceResponseDto normaliser(UUID invoiceId, UUID branchId);
}

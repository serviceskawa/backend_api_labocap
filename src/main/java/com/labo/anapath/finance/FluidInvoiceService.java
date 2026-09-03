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
    /**
     * Déclare la facture à la DGI, en l'encaissant d'abord si besoin.
     *
     * @param modeDePaiement le règlement à enregistrer — ESPECES, MOBILEMONEY,
     *                       CARTEBANCAIRE, VIREMENT, CHEQUES, AUTRE. Ignoré si
     *                       la facture est déjà réglée : son mode fait foi.
     */
    InvoiceResponseDto normaliser(UUID invoiceId, UUID branchId, String modeDePaiement);

    /**
     * Le document normalisé, récupéré chez FluidInvoice.
     *
     * <p>Transite par le serveur : l'accès est authentifié par la clé API, qui
     * ne doit jamais atteindre le navigateur.</p>
     */
    byte[] telechargerDocument(UUID invoiceId, UUID branchId);
}

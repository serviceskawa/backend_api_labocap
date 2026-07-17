package com.labo.anapath.finance;

import java.math.BigDecimal;

/**
 * Données de remboursement rattachées à une facture d'avoir (statusInvoice = 1).
 *
 * <p>Reproduit ce que la vue Laravel {@code invoices/show.blade.php} lit sur
 * {@code $refund} : le code de la demande de remboursement, le motif (affiché en
 * désignation de l'unique ligne du tableau), le montant, et le code de la facture
 * de vente d'origine (affiché en «Référence:»).</p>
 */
public record InvoiceRefundDto(
        String code,
        String reasonDescription,
        BigDecimal montant,
        String invoiceCode
) {}

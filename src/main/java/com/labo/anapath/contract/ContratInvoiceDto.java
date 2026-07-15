package com.labo.anapath.contract;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Informations résumées de la facture unique rattachée à un contrat.
 *
 * <p>Reproduit le bloc « Facture » de la vue détail Laravel : renseigné uniquement
 * lorsque le contrat est en facturation unique ({@code invoiceUnique = true}) et
 * qu'une facture existe pour ce contrat, sinon {@code null}.</p>
 */
public record ContratInvoiceDto(
        UUID id,
        String code,
        String clientName,
        Boolean isPaid,
        LocalDateTime paidAt,
        BigDecimal total
) {}

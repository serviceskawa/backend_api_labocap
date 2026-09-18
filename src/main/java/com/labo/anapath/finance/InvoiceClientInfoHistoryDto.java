package com.labo.anapath.finance;

import java.time.LocalDateTime;
import java.util.UUID;

/** Une ligne de l'historique des actualisations du nom/adresse client d'une facture. */
public record InvoiceClientInfoHistoryDto(
        UUID id,
        UUID userId,
        String userFullName,
        String oldClientName,
        String newClientName,
        String oldClientAddress,
        String newClientAddress,
        LocalDateTime createdAt
) {}

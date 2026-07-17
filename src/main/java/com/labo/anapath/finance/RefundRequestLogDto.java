package com.labo.anapath.finance;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ligne du tableau « Historique des demandes de remboursements ».
 *
 * @param userFullName nom affiché dans la colonne « Utilisateur »
 */
public record RefundRequestLogDto(
        UUID id,
        UUID userId,
        String userFullName,
        String operation,
        LocalDateTime createdAt
) {}

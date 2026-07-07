package com.labo.anapath.report;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ligne d'historique d'un compte-rendu (journal LogReport).
 */
public record LogReportResponseDto(
        UUID id,
        String action,
        String description,
        LocalDateTime date,
        String userFullName,
        UUID reportId,
        String reportCode,
        String testOrderCode
) {
}

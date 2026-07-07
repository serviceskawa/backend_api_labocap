package com.labo.anapath.report;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReportDetailDto(
        UUID id,
        String code,
        UUID testOrderId,
        String testOrderCode,
        String patientName,
        UUID titleId,
        String titleName,
        String content,
        String contentMicro,
        String comment,
        String commentSup,
        String descriptionSupplementaire,
        String descriptionSupplementaireMicro,
        ReportStatus status,
        boolean isDelivered,
        boolean isCalled,
        String receiverName,
        LocalDateTime signatureDate,
        LocalDateTime deliveryDate,
        LocalDateTime callDate,
        UUID signatory1Id,
        String signatory1Name,
        UUID signatory2Id,
        String signatory2Name,
        UUID signatory3Id,
        String signatory3Name,
        UUID reviewedById,
        String reviewedByName,
        List<String> tagNames,
        List<UUID> tagIds,
        List<LogReportDto> logs,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record LogReportDto(String action, String description, String userName, LocalDateTime createdAt) {}
}

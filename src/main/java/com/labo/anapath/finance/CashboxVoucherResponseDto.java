package com.labo.anapath.finance;

import com.labo.anapath.common.storage.FichierStocke;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CashboxVoucherResponseDto(
        UUID id,
        UUID cashboxId,
        String code,
        BigDecimal amount,
        String description,
        String status,
        UUID supplierId,
        String supplierName,
        UUID expenseCategoryId,
        String ticketFile,
        List<CashboxVoucherDetailResponseDto> details,
        UUID branchId,
        LocalDateTime createdAt
) {
    /** L'identifiant du fichier pour {@code GET /files/{id}}, déduit du chemin. */
    @JsonProperty
    public UUID fileId() {
        return FichierStocke.idPour(ticketFile);
    }
}

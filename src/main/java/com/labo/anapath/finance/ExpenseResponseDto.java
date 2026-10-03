package com.labo.anapath.finance;

import com.labo.anapath.common.storage.FichierStocke;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ExpenseResponseDto(
        UUID id,
        BigDecimal amount,
        String description,
        UUID supplierId,
        UUID expenseCategorieId,
        UUID cashboxVoucherId,
        Integer paid,
        LocalDate date,
        String invoiceNumber,
        String payment,
        String receipt,
        List<ExpenceDetailResponseDto> details,
        UUID branchId,
        LocalDateTime createdAt
) {
    /** L'identifiant du fichier pour {@code GET /files/{id}}, déduit du chemin. */
    @JsonProperty
    public UUID fileId() {
        return FichierStocke.idPour(receipt);
    }
}

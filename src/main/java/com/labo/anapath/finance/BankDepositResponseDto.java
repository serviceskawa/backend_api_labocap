package com.labo.anapath.finance;

import com.labo.anapath.common.storage.FichierStocke;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record BankDepositResponseDto(
        UUID id,
        UUID bankId,
        String bankName,
        UUID cashboxId,
        BigDecimal amount,
        LocalDate date,
        String description,
        String attachement,
        UUID branchId,
        LocalDateTime createdAt
) {
    /** L'identifiant du fichier pour {@code GET /files/{id}}, déduit du chemin. */
    @JsonProperty
    public UUID fileId() {
        return FichierStocke.idPour(attachement);
    }
}

package com.labo.anapath.finance;

import com.labo.anapath.common.storage.FichierStocke;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record RefundRequestResponseDto(
        UUID id,
        UUID invoiceId,
        /** Code lisible de la facture de référence (colonne « Facture référence »). */
        String invoiceCode,
        UUID refundReasonId,
        /** Libellé de la raison, affiché dans la colonne « Objet ». */
        String refundReasonLabel,
        BigDecimal montant,
        String note,
        String attachment,
        String code,
        String status,
        List<RefundRequestLogDto> logs,
        UUID branchId,
        LocalDateTime createdAt,
        /** Alimente la colonne « Dernière actualisation ». */
        LocalDateTime updatedAt
) {
    /** L'identifiant du fichier pour {@code GET /files/{id}}, déduit du chemin. */
    @JsonProperty
    public UUID fileId() {
        return FichierStocke.idPour(attachment);
    }
}

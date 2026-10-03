package com.labo.anapath.doc;

import com.labo.anapath.common.storage.FichierStocke;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocVersionResponseDto(
        UUID id,
        UUID docId,
        Integer version,
        String title,
        String attachment,
        Long fileSize,
        UUID userId,
        LocalDateTime createdAt
) {
    /** L'identifiant du fichier pour {@code GET /files/{id}}, déduit du chemin. */
    @JsonProperty
    public UUID fileId() {
        return FichierStocke.idPour(attachment);
    }
}

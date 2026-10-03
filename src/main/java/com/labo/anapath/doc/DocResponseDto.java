package com.labo.anapath.doc;

import com.labo.anapath.common.storage.FichierStocke;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocResponseDto(
        UUID id,
        String title,
        String attachment,
        Boolean isCurrentVersion,
        Long fileSize,
        UUID documentationCategoryId,
        UUID userId,
        UUID roleId,
        UUID branchId,
        LocalDateTime createdAt
) {
    /** L'identifiant du fichier pour {@code GET /files/{id}}, déduit du chemin. */
    @JsonProperty
    public UUID fileId() {
        return FichierStocke.idPour(attachment);
    }
}

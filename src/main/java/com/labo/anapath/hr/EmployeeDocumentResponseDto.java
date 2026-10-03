package com.labo.anapath.hr;

import com.labo.anapath.common.storage.FichierStocke;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.UUID;

public record EmployeeDocumentResponseDto(
        UUID id,
        UUID employeeId,
        String name,
        String type,
        @JsonIgnore String filePath,
        Long fileSize,
        UUID branchId,
        LocalDateTime createdAt
) {
    /** L'identifiant du fichier pour {@code GET /files/{id}}, déduit du chemin. */
    @JsonProperty
    public UUID fileId() {
        return FichierStocke.idPour(filePath);
    }
}

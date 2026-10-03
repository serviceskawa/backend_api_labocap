package com.labo.anapath.consultation;

import com.labo.anapath.common.storage.FichierStocke;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.UUID;

/** Représente un fichier joint à une consultation. */
public record ConsultationFileResponseDto(
        UUID id,
        String typeFileLabel,
        String path,
        String comment,
        LocalDateTime createdAt
) {
    /** L'identifiant du fichier pour {@code GET /files/{id}}, déduit du chemin. */
    @JsonProperty
    public UUID fileId() {
        return FichierStocke.idPour(path);
    }
}

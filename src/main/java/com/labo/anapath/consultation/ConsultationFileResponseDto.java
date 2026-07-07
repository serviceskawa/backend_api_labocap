package com.labo.anapath.consultation;

import java.time.LocalDateTime;
import java.util.UUID;

/** Représente un fichier joint à une consultation. */
public record ConsultationFileResponseDto(
        UUID id,
        String typeFileLabel,
        String path,
        String comment,
        LocalDateTime createdAt
) {}

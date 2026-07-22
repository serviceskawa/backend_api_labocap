package com.labo.anapath.support;

import java.time.LocalDateTime;
import java.util.UUID;

public record SignalResponseDto(
        UUID id,
        UUID testOrderId,
        /** Code du bon d'examen signalé — colonne « Code examen » de la liste Laravel. */
        String testOrderCode,
        String typeSignal,
        String commentaire,
        Boolean status,
        UUID userId,
        /** Nom de l'émetteur (lastname firstname) — colonne « Envoyé par » de la liste. */
        String userName,
        UUID branchId,
        LocalDateTime createdAt
) {}

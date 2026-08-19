package com.labo.anapath.testorder;

import java.util.List;
import java.util.UUID;

public record AssignmentDetailResponseDto(
        UUID id,
        UUID testOrderId,
        String testOrderCode,
        /**
         * Étiquettes physiques des prélèvements affectés — « L1 », « L2 »…
         * Vide pour les affectations antérieures à leur enregistrement.
         */
        List<String> labels,
        String note) {}

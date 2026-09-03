package com.labo.anapath.testorder;

import java.util.List;
import java.util.UUID;

public record AssignmentDetailResponseDto(
        UUID id,
        UUID testOrderId,
        String testOrderCode,

        /**
         * L'état de la demande — PENDING, VALIDATED, DELIVERED, CANCELLED.
         *
         * <p>C'est ce qu'on vient chercher en rouvrant un lot : savoir lesquels
         * de ses dossiers ont avancé. Sans lui, l'affectation ne dit que ce
         * qu'elle contient, jamais où en est chacun — et il fallait ouvrir les
         * demandes une à une pour le découvrir.</p>
         */
        String statutDemande,
        /**
         * Étiquettes physiques des prélèvements affectés — « L1 », « L2 »…
         * Vide pour les affectations antérieures à leur enregistrement.
         */
        List<String> labels,
        String note) {}

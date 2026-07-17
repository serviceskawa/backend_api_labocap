package com.labo.anapath.testorder;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de filtrage pour la liste paginée des bons d'examen.
 *
 * <p>Tous les champs sont optionnels. Les critères non renseignés (null) sont ignorés
 * par {@link TestOrderSpecification#filter(java.util.UUID, TestOrderFilterDto)}.
 * La recherche textuelle ({@code search}) porte sur le code du bon.
 */
@Getter
@Setter
public class TestOrderFilterDto {

    /** Filtre sur le statut du bon (PENDING, VALIDATED, DELIVERED, CANCELLED). */
    private TestOrderStatus status;

    /**
     * Filtre par statut du COMPTE RENDU : "NONE" (pas de compte rendu),
     * "DRAFT" (en attente), "VALIDATED" (validé), "DELIVERED" (livré).
     */
    private String reportStatus;

    /** Filtre sur l'identifiant du patient. */
    private UUID patientId;

    /** Filtre sur l'identifiant du médecin prescripteur. */
    private UUID doctorId;

    /** Filtre sur le pathologiste attribué (attribuate_doctor_id). */
    private UUID attribuateDoctorId;

    /** Filtre sur l'identifiant de l'hôpital d'origine. */
    private UUID hospitalId;

    /** Filtre sur l'urgence du bon. */
    private Boolean isUrgent;

    /** Date de prélèvement minimale (inclusif). */
    private LocalDate from;

    /** Date de prélèvement maximale (inclusif). */
    private LocalDate to;

    /** Texte recherché dans le code du bon (recherche insensible à la casse). */
    private String search;

    /** Filtre sur l'identifiant du contrat. */
    private UUID contratId;

    /** Filtre sur l'identifiant du type d'examen. */
    private UUID typeOrderId;
}

package com.labo.anapath.biology.results;

import com.labo.anapath.biology.BiologyKind;
import com.labo.anapath.report.ReportStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ligne de la liste de travail : une analyse d'un bon de biologie.
 *
 * @param analysisResultId identifiant de la ligne de saisie
 * @param status           état de la saisie
 * @param testOrderId      bon
 * @param orderCode        code du bon
 * @param urgent           bon urgent
 * @param orderCreatedAt   création du bon
 * @param prelevementDate  date du prélèvement
 * @param patientId        patient
 * @param patientCode      code patient
 * @param patientName      « NOM Prénoms »
 * @param labTestId        analyse
 * @param labTestName      nom de l'analyse
 * @param labTestCode      code de l'analyse
 * @param kind             PANEL ou CULTURE
 * @param categoryId       catégorie de l'analyse
 * @param categoryName     nom de la catégorie
 * @param enteredAt        dernière saisie
 * @param techValidatedAt  validation technique
 * @param reportId         compte-rendu du bon
 * @param reportStatus     état du compte-rendu
 */
public record BiologyWorklistRowDto(
        UUID analysisResultId,
        BiologyAnalysisStatus status,
        UUID testOrderId,
        String orderCode,
        boolean urgent,
        LocalDateTime orderCreatedAt,
        LocalDate prelevementDate,
        UUID patientId,
        String patientCode,
        String patientName,
        UUID labTestId,
        String labTestName,
        String labTestCode,
        BiologyKind kind,
        UUID categoryId,
        String categoryName,
        LocalDateTime enteredAt,
        LocalDateTime techValidatedAt,
        UUID reportId,
        ReportStatus reportStatus
) {}

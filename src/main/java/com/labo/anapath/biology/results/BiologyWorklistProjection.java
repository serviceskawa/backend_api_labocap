package com.labo.anapath.biology.results;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ligne brute de la liste de travail (requête native de
 * {@link BiologyAnalysisResultRepository#findWorklist}).
 */
public interface BiologyWorklistProjection {
    UUID getId();
    String getStatus();
    UUID getTestOrderId();
    String getOrderCode();
    Boolean getUrgent();
    LocalDateTime getOrderCreatedAt();
    LocalDate getPrelevementDate();
    UUID getPatientId();
    String getPatientCode();
    String getPatientFirstname();
    String getPatientLastname();
    UUID getLabTestId();
    String getLabTestName();
    String getLabTestCode();
    String getBiologyKind();
    UUID getCategoryId();
    String getCategoryName();
    LocalDateTime getEnteredAt();
    LocalDateTime getTechValidatedAt();
    UUID getReportId();
    String getReportStatus();
}

package com.labo.anapath.report;

import java.time.LocalDateTime;

/**
 * Projection Spring Data pour la requête native de recherche globale (page "Rechercher").
 * Mappée sur les colonnes alias de {@link ReportRepository#globalSearch}.
 */
public interface ReportGlobalSearchProjection {
    String getReportId();
    String getCodeReport();
    String getTestOrderId();
    String getCodeExamen();
    String getTypeExamen();
    String getContractName();
    String getPatientId();
    String getPatientFirstname();
    String getPatientLastname();
    String getDoctorId();
    String getDoctorName();
    String getHospitalId();
    String getHospitalName();
    String getReferenceHospital();
    LocalDateTime getDateCreation();
    Boolean getIsUrgent();
    /** Discipline du compte-rendu ({@code PATHOLOGY}, {@code BIOLOGY}). */
    String getDiscipline();
}

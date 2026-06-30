package com.labo.anapath.testorder;

/**
 * Projection des demandes d'examen dont le compte-rendu reste à faire au-delà du
 * seuil d'alerte (réplique Laravel {@code MailReportNonFait}).
 */
public interface ReportNonFaitProjection {

    /** Code de la demande d'examen. */
    String getTestOrderCode();

    /** Email du pathologiste assigné à notifier. */
    String getEmail();

    /** Nom complet du pathologiste assigné. */
    String getDoctorName();
}

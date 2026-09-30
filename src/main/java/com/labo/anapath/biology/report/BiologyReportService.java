package com.labo.anapath.biology.report;

import com.labo.anapath.report.ReportResponseDto;
import com.labo.anapath.report.ValidationSigneeDto;

import java.util.UUID;

/**
 * Validation biologique d'un compte-rendu de biologie, sa réouverture et sa
 * conclusion.
 *
 * <p>La validation passe par le cœur commun
 * ({@link com.labo.anapath.report.ReportService#validerCompteRendu}) : date de
 * signature, preuve d'appareil, journal et avis au patient sont ceux de
 * l'anatomie pathologique.</p>
 */
public interface BiologyReportService {

    /**
     * Valide un compte-rendu de biologie prêt ({@code PENDING_REVIEW}, toutes les
     * analyses prêtes selon {@code bio_validation_mode}, contrôle refait ici).
     * Le biologiste qui valide devient le signataire.
     *
     * @param preuve preuve d'appareil (application mobile), ou {@code null} depuis le web
     */
    ReportResponseDto validate(UUID reportId, UUID userId, UUID branchId, ValidationSigneeDto preuve);

    /**
     * Rouvre un compte-rendu validé mais pas encore remis : retour en
     * {@code DRAFT}, signature effacée, puis recalcul immédiat de l'état d'après
     * les analyses (qui gardent le leur).
     */
    ReportResponseDto reopen(UUID reportId, UUID userId, UUID branchId);

    /**
     * Enregistre la conclusion générale ({@code reports.comment}), imprimée en
     * fin de compte-rendu. Refusé une fois le compte-rendu validé.
     */
    ReportResponseDto updateConclusion(UUID reportId, String conclusion, UUID userId, UUID branchId);
}

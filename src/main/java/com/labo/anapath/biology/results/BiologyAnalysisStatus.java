package com.labo.anapath.biology.results;

/**
 * État de la saisie d'une analyse d'un bon de biologie
 * ({@code biology_analysis_results.status}).
 *
 * <p>Cycle : {@code PENDING} → {@code ENTERED} → {@code TECH_VALIDATED}, avec retour
 * de {@code TECH_VALIDATED} à {@code ENTERED} par l'annulation de la validation
 * technique (seul moyen de corriger une valeur validée).</p>
 */
public enum BiologyAnalysisStatus {
    /** Aucune valeur saisie. */
    PENDING,
    /** Au moins une valeur saisie ; modifiable. */
    ENTERED,
    /** Validée techniquement : verrouillée jusqu'à l'annulation de la validation. */
    TECH_VALIDATED
}

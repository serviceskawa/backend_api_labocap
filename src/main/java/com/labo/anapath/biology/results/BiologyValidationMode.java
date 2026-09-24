package com.labo.anapath.biology.results;

/**
 * Réglage {@code bio_validation_mode} : circuit de validation des résultats.
 */
public enum BiologyValidationMode {
    /** Le technicien valide chaque analyse, puis le biologiste le compte-rendu. */
    TWO_STEP,
    /** Le biologiste seul : saisir une analyse suffit à la rendre prête. */
    ONE_STEP
}

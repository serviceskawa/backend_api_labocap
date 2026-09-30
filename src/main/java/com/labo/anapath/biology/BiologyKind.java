package com.labo.anapath.biology;

/**
 * Nature d'une analyse de biologie ({@code lab_tests.biology_kind}).
 *
 * <p>Sans objet pour l'anatomie pathologique : la colonne reste nulle, ce que
 * garantit la contrainte {@code chk_lab_tests_biology_kind_discipline}.
 * Fixée à la création — passer d'une fiche de paramètres à une culture
 * rendrait orphelins les résultats déjà saisis.
 */
public enum BiologyKind {
    /** Fiche de paramètres chiffrés ou qualitatifs (NFS, ionogramme…). */
    PANEL,
    /** Culture bactériologique : options de culture, germes, antibiogramme. */
    CULTURE
}

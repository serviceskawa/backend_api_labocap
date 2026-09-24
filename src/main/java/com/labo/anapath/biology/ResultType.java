package com.labo.anapath.biology;

/**
 * Forme du résultat attendu pour un {@link BiologyParameter}.
 */
public enum ResultType {
    /** Valeur chiffrée, comparée aux valeurs de référence pour poser un indicateur. */
    NUMERIC,
    /** Texte libre, sans indicateur. */
    TEXT,
    /** Une valeur parmi la liste {@link BiologyParameter#getChoices()}. */
    CHOICE
}

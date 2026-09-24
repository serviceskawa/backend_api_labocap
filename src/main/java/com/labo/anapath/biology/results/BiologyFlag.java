package com.labo.anapath.biology.results;

/**
 * Indicateur posé sur la valeur d'un paramètre ({@code biology_parameter_results.flag}).
 *
 * <p>Absence d'indicateur ({@code null}) : paramètre non signalable, valeur non
 * chiffrée sans indicateur manuel, ou aucune valeur de référence applicable.</p>
 */
public enum BiologyFlag {
    /** Dans les valeurs de référence. */
    N,
    /** Sous la borne basse. */
    L,
    /** Au-dessus de la borne haute. */
    H,
    /** Sous le seuil critique bas. */
    LL,
    /** Au-dessus du seuil critique haut. */
    HH,
    /** Anormal : posé à la main, sur un résultat TEXT ou CHOICE seulement. */
    A
}

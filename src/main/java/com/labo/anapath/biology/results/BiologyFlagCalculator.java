package com.labo.anapath.biology.results;

import com.labo.anapath.biology.BiologyReferenceRange;
import com.labo.anapath.biology.ResultType;
import com.labo.anapath.common.exception.BusinessException;

import java.math.BigDecimal;
import java.util.Locale;

/**
 * Indicateur d'une valeur de paramètre. Pur : ni base, ni horloge.
 *
 * <h2>Calcul automatique (paramètre NUMERIC signalable)</h2>
 * Les bornes sont <b>incluses</b> dans l'intervalle qu'elles ferment — la normale
 * est {@code [low, high]}, le non-critique {@code [criticalLow, criticalHigh]} :
 * <ol>
 *   <li>valeur &lt; seuil critique bas → {@code LL} ;</li>
 *   <li>valeur &gt; seuil critique haut → {@code HH} ;</li>
 *   <li>valeur &lt; borne basse → {@code L} ;</li>
 *   <li>valeur &gt; borne haute → {@code H} ;</li>
 *   <li>sinon {@code N} — dès qu'au moins une borne existe.</li>
 * </ol>
 * Aucune plage applicable, ou plage sans aucune borne : pas d'indicateur.
 * Une valeur égale au seuil critique est donc {@code L}/{@code H}, pas critique.
 *
 * <h2>Sans calcul</h2>
 * TEXT et CHOICE : pas d'indicateur automatique. Paramètre non signalable
 * ({@code flaggable = false}) : jamais d'indicateur.
 *
 * <h2>Indicateur manuel</h2>
 * Permis sur un paramètre signalable seulement ; il remplace le calcul et marque
 * {@code flag_overridden}. Valeurs admises :
 * <ul>
 *   <li>NUMERIC : {@code N}, {@code L}, {@code H}, {@code LL}, {@code HH} ;</li>
 *   <li>TEXT, CHOICE : {@code A} (anormal) seulement.</li>
 * </ul>
 * Il n'est pas mémorisé d'un enregistrement à l'autre : l'écran le renvoie tant
 * qu'il veut le garder ({@code flagOverridden} lui dit qu'il y en a un).
 */
public final class BiologyFlagCalculator {

    private BiologyFlagCalculator() {
    }

    /**
     * Bornes d'une plage de référence.
     */
    public record Bornes(BigDecimal low, BigDecimal high, BigDecimal criticalLow, BigDecimal criticalHigh) {

        /** Bornes d'une plage résolue, ou {@code null} si aucune plage. */
        public static Bornes de(BiologyReferenceRange plage) {
            return plage == null ? null
                    : new Bornes(plage.getLow(), plage.getHigh(), plage.getCriticalLow(), plage.getCriticalHigh());
        }

        /** {@code true} si aucune borne n'est renseignée. */
        public boolean vide() {
            return low == null && high == null && criticalLow == null && criticalHigh == null;
        }
    }

    /**
     * Indicateur retenu.
     *
     * @param flag       indicateur, ou {@code null}
     * @param overridden {@code true} s'il a été posé à la main
     */
    public record Indicateur(BiologyFlag flag, boolean overridden) {
        static final Indicateur AUCUN = new Indicateur(null, false);
    }

    /**
     * Indicateur calculé, sans indicateur manuel.
     *
     * @param type      forme du résultat
     * @param flaggable paramètre signalable
     * @param valeur    valeur chiffrée (arrondie), pour un NUMERIC
     * @param bornes    bornes de la plage applicable, ou {@code null}
     * @return l'indicateur, ou {@code null}
     */
    public static BiologyFlag calculer(ResultType type, boolean flaggable, BigDecimal valeur, Bornes bornes) {
        if (!flaggable || type != ResultType.NUMERIC || valeur == null || bornes == null || bornes.vide()) {
            return null;
        }
        if (bornes.criticalLow() != null && valeur.compareTo(bornes.criticalLow()) < 0) {
            return BiologyFlag.LL;
        }
        if (bornes.criticalHigh() != null && valeur.compareTo(bornes.criticalHigh()) > 0) {
            return BiologyFlag.HH;
        }
        if (bornes.low() != null && valeur.compareTo(bornes.low()) < 0) {
            return BiologyFlag.L;
        }
        if (bornes.high() != null && valeur.compareTo(bornes.high()) > 0) {
            return BiologyFlag.H;
        }
        return BiologyFlag.N;
    }

    /**
     * Indicateur retenu : le manuel s'il est fourni (et permis), sinon le calcul.
     *
     * @param type          forme du résultat
     * @param flaggable     paramètre signalable
     * @param valeur        valeur chiffrée (arrondie), pour un NUMERIC
     * @param bornes        bornes de la plage applicable, ou {@code null}
     * @param manuel        indicateur manuel brut ({@code "H"}, {@code "a"}…), ou {@code null}/vide
     * @param nomParametre  libellé du paramètre, pour le message d'erreur
     * @return l'indicateur retenu
     * @throws BusinessException si l'indicateur manuel est inconnu ou non permis
     */
    public static Indicateur determiner(ResultType type, boolean flaggable, BigDecimal valeur, Bornes bornes,
                                        String manuel, String nomParametre) {
        BiologyFlag force = lireManuel(manuel, nomParametre);
        if (force == null) {
            BiologyFlag calcule = calculer(type, flaggable, valeur, bornes);
            return calcule == null ? Indicateur.AUCUN : new Indicateur(calcule, false);
        }
        if (!flaggable) {
            throw new BusinessException("« " + nomParametre + " » ne porte pas d'indicateur : "
                    + "l'indicateur manuel n'est pas permis.");
        }
        boolean chiffre = type == ResultType.NUMERIC;
        if (chiffre && force == BiologyFlag.A) {
            throw new BusinessException("« " + nomParametre + " » est chiffré : son indicateur est N, L, H, LL "
                    + "ou HH ; « A » (anormal) est réservé aux résultats en texte ou à choix.");
        }
        if (!chiffre && force != BiologyFlag.A) {
            throw new BusinessException("« " + nomParametre + " » n'est pas chiffré : seul l'indicateur "
                    + "« A » (anormal) peut y être posé.");
        }
        return new Indicateur(force, true);
    }

    /**
     * @return l'indicateur manuel lu, ou {@code null} si absent
     * @throws BusinessException si la valeur n'est pas un indicateur connu
     */
    static BiologyFlag lireManuel(String manuel, String nomParametre) {
        if (manuel == null || manuel.isBlank()) {
            return null;
        }
        try {
            return BiologyFlag.valueOf(manuel.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Indicateur « " + manuel.strip() + " » inconnu pour « " + nomParametre
                    + " » : N, L, H, LL, HH ou A.");
        }
    }
}

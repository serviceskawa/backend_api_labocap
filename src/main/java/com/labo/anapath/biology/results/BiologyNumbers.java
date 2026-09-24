package com.labo.anapath.biology.results;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/**
 * Lecture, arrondi et écriture des valeurs chiffrées de biologie. Sans état, sans base.
 *
 * <h2>Lecture</h2>
 * Le laboratoire tape à la française : la virgule est acceptée comme séparateur
 * décimal au même titre que le point. Les espaces (y compris insécables) servent de
 * séparateur de milliers et sont ignorés. Refusés : les deux séparateurs à la fois
 * (« 1.234,5 » est ambigu), la notation scientifique, les valeurs censurées
 * (« &lt;0,5 », « &gt;1000 ») — une telle valeur se saisit sur un paramètre TEXT.
 *
 * <h2>Arrondi (décision)</h2>
 * La valeur est <b>arrondie au nombre de décimales du paramètre</b> (demi vers le
 * haut) avant d'être stockée, et c'est la valeur arrondie qui est comparée aux
 * valeurs de référence : l'indicateur correspond toujours au chiffre imprimé. Sans
 * {@code decimals}, on garde la précision de la colonne (4 décimales).
 */
public final class BiologyNumbers {

    /** Échelle de {@code value_numeric} et des bornes : NUMERIC(14,4). */
    public static final int ECHELLE_MAX = 4;

    /** Plus grande valeur absolue exclue qu'accepte NUMERIC(14,4) : 10^10. */
    private static final BigDecimal LIMITE = BigDecimal.TEN.pow(14 - ECHELLE_MAX);

    private static final Pattern NOMBRE = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)");

    private BiologyNumbers() {
    }

    /**
     * @param brut valeur saisie (« 12,5 », « 1 250 », « -0.3 »)
     * @return le nombre, ou {@code null} si la saisie n'est pas un nombre lisible
     */
    public static BigDecimal lire(String brut) {
        if (brut == null) {
            return null;
        }
        String s = brut.strip().replaceAll("[\\s\\u00A0\\u202F]", "");
        if (s.isEmpty() || (s.indexOf(',') >= 0 && s.indexOf('.') >= 0)) {
            return null;
        }
        s = s.replace(',', '.');
        if (!NOMBRE.matcher(s).matches()) {
            return null;
        }
        return new BigDecimal(s);
    }

    /**
     * Arrondit au nombre de décimales du paramètre (demi vers le haut), borné à
     * l'échelle de la colonne.
     *
     * @param valeur   nombre lu
     * @param decimals décimales du paramètre, ou {@code null}
     * @return la valeur arrondie
     */
    public static BigDecimal arrondir(BigDecimal valeur, Short decimals) {
        int echelle = decimals == null ? ECHELLE_MAX : Math.max(0, Math.min(decimals, ECHELLE_MAX));
        BigDecimal v = valeur.setScale(echelle, RoundingMode.HALF_UP);
        // Sans décimales imposées, « 12.5000 » s'écrit « 12.5 » : on n'invente pas de précision.
        return decimals == null ? sansZerosInutiles(v) : v;
    }

    /** {@code true} si la valeur tient dans NUMERIC(14,4). */
    public static boolean tientEnBase(BigDecimal valeur) {
        return valeur.abs().compareTo(LIMITE) < 0;
    }

    /**
     * Écriture française d'une borne ou d'une valeur (virgule décimale).
     *
     * @param valeur   nombre
     * @param decimals décimales imposées, ou {@code null} pour ôter les zéros inutiles
     */
    public static String ecrire(BigDecimal valeur, Short decimals) {
        if (valeur == null) {
            return null;
        }
        BigDecimal v = decimals == null ? sansZerosInutiles(valeur)
                : valeur.setScale(Math.max(0, Math.min(decimals, ECHELLE_MAX)), RoundingMode.HALF_UP);
        return v.toPlainString().replace('.', ',');
    }

    /**
     * Valeurs de référence telles qu'imprimées : « 12,0 – 16,0 », « ≥ 3 », « ≤ 5 ».
     *
     * @return le texte, ou {@code null} si aucune borne normale n'est renseignée
     */
    public static String intervalle(BigDecimal low, BigDecimal high, Short decimals) {
        if (low != null && high != null) {
            return ecrire(low, decimals) + " – " + ecrire(high, decimals);
        }
        if (low != null) {
            return "≥ " + ecrire(low, decimals);
        }
        if (high != null) {
            return "≤ " + ecrire(high, decimals);
        }
        return null;
    }

    private static BigDecimal sansZerosInutiles(BigDecimal v) {
        BigDecimal s = v.stripTrailingZeros();
        return s.scale() < 0 ? s.setScale(0) : s;
    }
}

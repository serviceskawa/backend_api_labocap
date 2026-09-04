package com.labo.anapath.common.notification;

/**
 * Mise au format international des numéros de téléphone avant envoi à OurVoice.
 *
 * <p><b>RÈGLE R7.</b> Les numéros sont saisis au format local béninois
 * ({@code 97000001}) alors qu'OurVoice attend l'indicatif pays collé au numéro
 * ({@code 22997000001}). La règle vivait en dur, dupliquée, dans le service de
 * notification des comptes-rendus ; la notification des factures en a le même
 * besoin, d'où cette extraction.</p>
 *
 * <p>La normalisation va au-delà du simple préfixe : les numéros réels portent
 * des espaces, des points ou un {@code +} de tête, et un numéro déjà international
 * ne doit pas se voir préfixer une seconde fois — {@code 22922997000001} partirait
 * chez OurVoice sans qu'aucune erreur ne remonte, le SMS étant simplement perdu.</p>
 */
public final class PhoneNumbers {

    /** Indicatif du Bénin, sans {@code +}, tel qu'attendu par OurVoice. */
    public static final String INDICATIF_BENIN = "229";

    /** Longueur d'un numéro béninois sans indicatif. */
    private static final int LONGUEUR_NUMERO_LOCAL = 8;

    private PhoneNumbers() {
    }

    /**
     * Met un numéro au format attendu par OurVoice.
     *
     * @param numero numéro tel que saisi ; peut contenir espaces, points, tirets,
     *               parenthèses ou un {@code +} de tête
     * @return le numéro en chiffres, préfixé de l'indicatif béninois, ou
     *         {@code null} si l'entrée est vide ou ne contient aucun chiffre
     */
    public static String toInternational(String numero) {
        if (numero == null) {
            return null;
        }
        String chiffres = numero.replaceAll("[^0-9]", "");
        if (chiffres.isEmpty()) {
            return null;
        }
        // 00229… est la forme internationale composée depuis un poste fixe.
        if (chiffres.startsWith("00")) {
            chiffres = chiffres.substring(2);
        }
        // Un numéro béninois local fait 8 chiffres, et un fixe peut commencer par
        // « 229 » (par exemple 229 12 34 5). Le seul « 229 » de tête ne suffit donc
        // pas à conclure : on n'y voit l'indicatif que si le numéro est plus long
        // qu'un numéro local.
        if (chiffres.length() > LONGUEUR_NUMERO_LOCAL && chiffres.startsWith(INDICATIF_BENIN)) {
            return chiffres;
        }
        return INDICATIF_BENIN + chiffres;
    }
}

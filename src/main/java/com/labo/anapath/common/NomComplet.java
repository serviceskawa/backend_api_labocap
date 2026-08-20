package com.labo.anapath.common;

/**
 * Écriture d'un nom de personne, dans l'ordre du pays.
 *
 * <h2>Pourquoi cette classe existe</h2>
 *
 * <p>L'identité d'une personne était composée à la main partout où l'on en
 * avait besoin — {@code getFirstname() + " " + getLastname()} — soit une
 * vingtaine d'endroits qui rendaient tous « Prénom Nom ». Au Bénin comme
 * ailleurs en Afrique francophone, on écrit et on appelle dans l'autre sens :
 * le nom d'abord, les prénoms ensuite. Les listes de la plateforme le faisaient
 * déjà, à l'inverse des documents imprimés.</p>
 *
 * <p>Une expression recopiée vingt fois finit toujours par diverger. Il n'y a
 * plus qu'un endroit à changer si la convention change.</p>
 *
 * <h2>Ce que cette classe ne fait pas</h2>
 *
 * <p>Elle ne touche pas au stockage. {@code lastname} porte le nom et
 * {@code firstname} les prénoms, comme les formulaires de saisie l'indiquent ;
 * seule la restitution est concernée.</p>
 */
public final class NomComplet {

    private NomComplet() {
    }

    /**
     * « NOM Prénoms », dans cet ordre.
     *
     * <p>Tolère les valeurs absentes : une fiche incomplète doit rendre ce
     * qu'elle a plutôt qu'un « null » ou une espace esseulée. Un patient
     * enregistré à la hâte sous son seul nom reste identifiable.</p>
     *
     * @param nom     le nom de famille ({@code lastname})
     * @param prenoms les prénoms ({@code firstname})
     */
    public static String de(String nom, String prenoms) {
        String n = nom == null ? "" : nom.trim();
        String p = prenoms == null ? "" : prenoms.trim();
        if (n.isEmpty()) return p;
        if (p.isEmpty()) return n;
        return n + " " + p;
    }
}

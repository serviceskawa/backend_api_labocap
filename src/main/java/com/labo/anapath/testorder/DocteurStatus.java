package com.labo.anapath.testorder;

/**
 * Où en est le médecin sur une demande qui lui est affectée.
 *
 * <p>Distinct du statut du compte rendu, et volontairement. Celui-ci dit où en
 * est le document — brouillon, en relecture, validé, remis. Celui-là dit où en
 * est la personne : elle ne l'a pas encore ouvert, elle s'en occupe, elle en a
 * fini. Les deux avancent à des rythmes différents, et confondre « j'ai fini de
 * lire » avec « le compte rendu est validé » ferait disparaître de la file de
 * travail un dossier encore à écrire.</p>
 *
 * <p>Il ne se change que depuis le web : sur le téléphone, la file est en
 * lecture. C'est la maquette qui le pose, et cela se tient — on marque un
 * dossier « terminé » en le refermant sur un poste de travail, pas en le
 * consultant entre deux couloirs.</p>
 */
public enum DocteurStatus {

    /** Affectée, pas encore ouverte. L'état de départ, posé à l'ajout au lot. */
    A_TRAITER("a_traiter"),

    /** Le médecin s'en occupe. */
    PRIS_EN_CHARGE("pris_en_charge"),

    /**
     * Le médecin en a fini.
     *
     * <p>La demande reste visible dans la file le jour même, puis en disparaît
     * le lendemain : la retirer aussitôt ferait douter d'avoir bien enregistré
     * ce qu'on venait de faire.</p>
     */
    TERMINE("termine");

    private final String valeur;

    DocteurStatus(String valeur) {
        this.valeur = valeur;
    }

    /** La forme écrite en base, en minuscules, telle que la maquette la nomme. */
    public String valeur() {
        return valeur;
    }

    /**
     * Relit une valeur de la base ou d'une requête.
     *
     * <p>Une valeur inconnue rend {@link #A_TRAITER} plutôt que de lever : une
     * demande dont le statut serait illisible doit rester dans la file, où
     * quelqu'un la verra, plutôt que d'en disparaître en silence.</p>
     */
    public static DocteurStatus depuis(String brut) {
        if (brut == null) return A_TRAITER;
        String propre = brut.trim().toLowerCase();
        for (DocteurStatus s : values()) {
            if (s.valeur.equals(propre)) return s;
        }
        return A_TRAITER;
    }
}

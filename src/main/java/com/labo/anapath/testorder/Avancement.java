package com.labo.anapath.testorder;

/**
 * Où en est réellement le travail du médecin sur un dossier.
 *
 * <h2>Déduit, jamais déclaré</h2>
 *
 * <p>Le suivi manuel {@link DocteurStatus} existait pour cela et ne le disait
 * pas : il ne se change que depuis le web, et personne ne le change. Relevé en
 * production, un médecin avait 172 dossiers marqués « à traiter » dont 124
 * étaient déjà remis au client, compte rendu compris. Le chiffre était exact au
 * sens de la case cochée, et faux au sens de tout le reste.</p>
 *
 * <p>L'avancement se lit donc sur le compte rendu, qui avance parce qu'on
 * travaille dessus. Rien à tenir à jour, rien à oublier.</p>
 */
public enum Avancement {

    /** Aucun compte rendu, ou un brouillon : c'est ce qui reste à écrire. */
    A_TRAITER("a_traiter"),

    /** Compte rendu soumis, en attente de relecture. */
    EN_RELECTURE("en_relecture"),

    /** Compte rendu validé ou remis : le médecin en a fini. */
    TERMINE("termine");

    private final String valeur;

    Avancement(String valeur) {
        this.valeur = valeur;
    }

    public String valeur() {
        return valeur;
    }

    /**
     * L'avancement que trahit l'état d'un compte rendu.
     *
     * <p>Une seule définition, partagée par le filtre, les compteurs et la
     * pastille de chaque ligne. Les écrire séparément — l'un en SQL, l'autre
     * sur le téléphone — les ferait diverger dès la première retouche, et une
     * liste contredirait son propre en-tête.</p>
     *
     * @param etatDuCompteRendu DRAFT, PENDING_REVIEW, VALIDATED, DELIVERED, ou
     *                          {@code null} quand aucun compte rendu n'existe
     */
    public static Avancement selonLeCompteRendu(String etatDuCompteRendu) {
        if (etatDuCompteRendu == null) return A_TRAITER;
        return switch (etatDuCompteRendu) {
            case "PENDING_REVIEW" -> EN_RELECTURE;
            case "VALIDATED", "DELIVERED" -> TERMINE;
            // Un brouillon reste à écrire, et un état inconnu ne doit pas
            // faire disparaître un dossier du travail à faire.
            default -> A_TRAITER;
        };
    }

    /** L'avancement nommé, ou {@code null} si le nom n'en désigne aucun. */
    public static Avancement depuis(String valeur) {
        if (valeur == null) return null;
        for (Avancement a : values()) {
            if (a.valeur.equalsIgnoreCase(valeur)) return a;
        }
        return null;
    }
}

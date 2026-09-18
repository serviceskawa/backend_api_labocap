package com.labo.anapath.testorder;

import java.util.List;
import java.util.UUID;

/**
 * Ce que le médecin cherche dans sa file.
 *
 * <p>Tous les champs sont facultatifs ; un champ nul ne restreint rien. Ils
 * étaient jusqu'ici appliqués sur le téléphone, sur la file entière tenue en
 * mémoire. C'était tenable tant que la file descendait d'un bloc ; elle est
 * désormais paginée, et un filtre posé sur la page courante ne verrait qu'elle.
 *
 * @param annee          l'année d'ouverture des dossiers
 * @param lot            le code d'une affectation — « AF26-0001 »
 * @param docteurStatus  où en est le médecin : a_traiter, pris_en_charge, termine
 * @param statutDemande  l'état de la demande : PENDING, VALIDATED, DELIVERED…
 * @param urgents        ne garder que les dossiers marqués urgents et non remis
 * @param enRetard       ne garder que ceux qui dépassent le délai sans compte rendu
 * @param demandes       restreindre à ces demandes précises
 */
public record FiltreFileDuMedecin(
        Integer annee,
        String lot,
        String docteurStatus,
        String statutDemande,
        Boolean urgents,
        Boolean enRetard,

        /**
         * Restreindre à ces demandes précises.
         *
         * <p>Sert au filtre « non lus » : les messages non lus se comptent dans
         * le service des discussions, et le téléphone en connaît déjà la liste —
         * neuf fils en tout dans cette installation. Il envoie donc les
         * identifiants concernés plutôt que d'obliger cette requête à joindre
         * les lectures de discussion pour un cas rare et minuscule.</p>
         *
         * <p>Une liste vide n'est pas un filtre absent : elle demande une page
         * vide, et c'est bien ce qu'il faut rendre quand rien n'est non lu.</p>
         */
        List<UUID> demandes,

        /**
         * Écarter les dossiers que le médecin a terminés.
         *
         * <p>Distinct de {@code docteurStatus}, qui désigne un état précis :
         * ici on en retire un. C'est la position d'ouverture de l'écran — on
         * vient y voir ce qui reste, pas relire ce qu'on a rendu.</p>
         */
        Boolean exclureTermines,

        /**
         * Où en est le travail — a_traiter, en_relecture, termine.
         *
         * <p>Déduit du compte rendu et non du suivi manuel : voir
         * {@link Avancement}.</p>
         */
        String avancement) {

    /** Le filtre qui ne restreint rien. */
    public static FiltreFileDuMedecin aucun() {
        return new FiltreFileDuMedecin(null, null, null, null, null, null, null, null, null);
    }

    /**
     * Le même périmètre, restreint à un avancement.
     *
     * <p>Ces dérivations existent pour que les comptages ne s'écrivent pas en
     * alignant neuf arguments positionnels, où l'on décale un {@code null} sans
     * que rien ne le signale.</p>
     */
    public FiltreFileDuMedecin avecAvancement(Avancement etape) {
        return new FiltreFileDuMedecin(annee, lot, docteurStatus, statutDemande,
                urgents, enRetard, demandes, exclureTermines,
                etape == null ? null : etape.valeur());
    }

    /** Le même périmètre, restreint à un état de la demande. */
    public FiltreFileDuMedecin avecStatutDemande(TestOrderStatus statut) {
        return new FiltreFileDuMedecin(annee, lot, docteurStatus,
                statut == null ? null : statut.name(), urgents, enRetard,
                demandes, exclureTermines, avancement);
    }

    /** Le même périmètre, sous un critère d'urgence ou de retard imposé. */
    public FiltreFileDuMedecin avecAlerte(Boolean urgents, Boolean enRetard) {
        return new FiltreFileDuMedecin(annee, lot, docteurStatus, statutDemande,
                urgents, enRetard, demandes, exclureTermines, avancement);
    }

    /** Le même filtre, sans la restriction de statut du médecin. */
    public FiltreFileDuMedecin sansStatutDuMedecin() {
        // « Exclure les terminés » part avec le reste : c'est le statut du
        // médecin qu'on décompose, et le garder mettrait « terminées » à zéro.
        return new FiltreFileDuMedecin(
                annee, lot, null, statutDemande, urgents, enRetard, demandes, null,
                avancement);
    }
}

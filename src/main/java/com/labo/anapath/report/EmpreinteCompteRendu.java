package com.labo.anapath.report;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Photographie des champs signifiants d'un compte-rendu, prise avant une
 * modification pour savoir ce qui a réellement changé.
 *
 * <p>Sert la traçabilité des modifications survenues <b>après signature</b> :
 * l'enregistrement du formulaire réécrit tous les champs à chaque fois, y
 * compris à l'identique. Comparer avant/après évite de journaliser — et de
 * notifier — une simple ouverture suivie d'un clic sur « Enregistrer ».</p>
 *
 * <p>Seuls les champs que lit un médecin sont suivis. Les horodatages
 * techniques ({@code updatedAt}, {@code signatureDate}) sont écartés : ils
 * changent à chaque sauvegarde et signaleraient une modification là où il n'y
 * en a pas.</p>
 */
record EmpreinteCompteRendu(Map<String, String> valeurs) {

    /** Prend l'empreinte d'un compte-rendu dans son état courant. */
    static EmpreinteCompteRendu de(Report report) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("Titre", identifiant(report.getTitleReport() != null
                ? report.getTitleReport().getId() : null));
        v.put("Contenu macroscopique", report.getContent());
        v.put("Contenu microscopique", report.getContentMicro());
        v.put("Commentaire", report.getComment());
        v.put("Commentaire supplémentaire", report.getCommentSup());
        v.put("Description complémentaire", report.getDescriptionSupplementaire());
        v.put("Description complémentaire (micro)", report.getDescriptionSupplementaireMicro());
        v.put("Nom du récupérateur", report.getReceiverName());
        v.put("Signataire 1", identifiant(report.getSignatory1() != null
                ? report.getSignatory1().getId() : null));
        v.put("Signataire 2", identifiant(report.getSignatory2() != null
                ? report.getSignatory2().getId() : null));
        v.put("Signataire 3", identifiant(report.getSignatory3() != null
                ? report.getSignatory3().getId() : null));
        v.put("État", report.getStatus() != null ? report.getStatus().name() : null);
        return new EmpreinteCompteRendu(v);
    }

    /**
     * Noms des champs qui diffèrent entre cette empreinte et l'état passé.
     *
     * @return liste vide si rien n'a bougé — l'appelant n'a alors rien à tracer
     */
    List<String> champsModifies(Report apres) {
        Map<String, String> nouvelles = de(apres).valeurs();
        List<String> changes = new ArrayList<>();
        for (Map.Entry<String, String> e : valeurs.entrySet()) {
            if (!Objects.equals(normaliser(e.getValue()), normaliser(nouvelles.get(e.getKey())))) {
                changes.add(e.getKey());
            }
        }
        return changes;
    }

    /**
     * {@code null} et chaîne vide décrivent le même fait — un champ non
     * renseigné — mais le service écrit tantôt l'un tantôt l'autre selon le
     * champ. Sans cette normalisation, passer de {@code null} à {@code ""}
     * compterait comme une modification et déclencherait une alerte à vide.
     */
    private static String normaliser(String valeur) {
        return valeur == null || valeur.isBlank() ? "" : valeur;
    }

    private static String identifiant(UUID id) {
        return id != null ? id.toString() : null;
    }
}

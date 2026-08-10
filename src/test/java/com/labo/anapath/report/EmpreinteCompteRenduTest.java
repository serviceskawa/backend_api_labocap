package com.labo.anapath.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.labo.anapath.user.User;

/**
 * Le cœur du dispositif de traçabilité après signature : distinguer une vraie
 * modification d'un simple réenregistrement.
 *
 * <p>Le formulaire réécrit tous les champs à chaque sauvegarde. Sans cette
 * distinction, ouvrir un compte-rendu signé et cliquer sur « Enregistrer »
 * alerterait les administrateurs pour rien — et l'alerte, banalisée, cesserait
 * d'être lue.</p>
 */
class EmpreinteCompteRenduTest {

    private Report compteRenduSigne() {
        Report r = new Report();
        r.setContent("<p>Aspect macroscopique</p>");
        r.setContentMicro("<p>Aspect microscopique</p>");
        r.setComment("Commentaire");
        r.setStatus(ReportStatus.VALIDATED);
        User medecin = new User();
        medecin.setId(UUID.randomUUID());
        r.setSignatory1(medecin);
        return r;
    }

    @Test
    @DisplayName("Réenregistrement à l'identique → aucune modification")
    void reenregistrementIdentique() {
        Report report = compteRenduSigne();
        EmpreinteCompteRendu avant = EmpreinteCompteRendu.de(report);

        assertThat(avant.champsModifies(report)).isEmpty();
    }

    @Test
    @DisplayName("Contenu macroscopique retouché → champ signalé")
    void contenuRetouche() {
        Report report = compteRenduSigne();
        EmpreinteCompteRendu avant = EmpreinteCompteRendu.de(report);

        report.setContent("<p>Aspect macroscopique corrigé</p>");

        assertThat(avant.champsModifies(report))
                .containsExactly("Contenu macroscopique");
    }

    @Test
    @DisplayName("Complément ajouté après signature → champ signalé")
    void complementAjoute() {
        Report report = compteRenduSigne();
        EmpreinteCompteRendu avant = EmpreinteCompteRendu.de(report);

        report.setDescriptionSupplementaire("<p>Complément.</p>");

        assertThat(avant.champsModifies(report))
                .containsExactly("Description complémentaire");
    }

    @Test
    @DisplayName("Changement de signataire → champ signalé")
    void signataireChange() {
        Report report = compteRenduSigne();
        EmpreinteCompteRendu avant = EmpreinteCompteRendu.de(report);

        User autre = new User();
        autre.setId(UUID.randomUUID());
        report.setSignatory1(autre);

        assertThat(avant.champsModifies(report)).containsExactly("Signataire 1");
    }

    /**
     * Le service écrit "" là où la base contenait {@code null} pour les champs
     * complémentaires. Sans normalisation, chaque enregistrement d'un
     * compte-rendu sans complément déclencherait une fausse alerte.
     */
    @Test
    @DisplayName("null devenu chaîne vide → aucune modification")
    void nullVersChaineVide() {
        Report report = compteRenduSigne();
        report.setDescriptionSupplementaire(null);
        EmpreinteCompteRendu avant = EmpreinteCompteRendu.de(report);

        report.setDescriptionSupplementaire("");

        assertThat(avant.champsModifies(report)).isEmpty();
    }

    @Test
    @DisplayName("Plusieurs champs touchés → tous signalés, dans l'ordre du formulaire")
    void plusieursChamps() {
        Report report = compteRenduSigne();
        EmpreinteCompteRendu avant = EmpreinteCompteRendu.de(report);

        report.setContent("<p>Autre macro</p>");
        report.setComment("Autre commentaire");

        assertThat(avant.champsModifies(report))
                .containsExactly("Contenu macroscopique", "Commentaire");
    }
}

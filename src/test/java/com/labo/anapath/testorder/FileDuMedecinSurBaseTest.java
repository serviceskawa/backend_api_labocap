package com.labo.anapath.testorder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * La file du médecin, soumise à un vrai PostgreSQL.
 *
 * <h2>Pourquoi ce test existe</h2>
 *
 * <p>Une requête peut compiler, passer tous les tests unitaires, et rester
 * inexécutable. C'est arrivé : la forme {@code (:borne IS NULL OR colonne >=
 * :borne)} a mis la file hors service en production, PostgreSQL refusant de
 * typer un paramètre qu'il ne rencontre que dans un test de nullité. Rien
 * n'avait pu l'arrêter — aucun test ne touchait ce point d'entrée, et le
 * workflow de déploiement n'en lance aucun.</p>
 *
 * <p>Ce test ne vérifie donc pas un résultat mais une chose plus élémentaire :
 * que chaque combinaison de filtres produit un SQL que la base accepte. Sur une
 * base vide, ce qui suffit — c'est la grammaire qu'on éprouve, pas les
 * données.</p>
 */
/*
 * Ne s'exécute que si on lui désigne une base. Sans cette garde, il échouerait
 * partout où il n'y en a pas — y compris dans une intégration continue qui n'en
 * fournit pas encore — et rejoindrait le bruit de fond qu'il est censé réduire.
 *
 *   docker run -d --name pg-verif --network verif-labo \
 *     -e POSTGRES_DB=labo -e POSTGRES_USER=labo -e POSTGRES_PASSWORD=labo postgres:16
 *   # y charger un schéma (dump structure seule d'une base existante)
 *   VERIF_DB_URL=jdbc:postgresql://pg-verif:5432/labo mvn test -Dtest=FileDuMedecinSurBaseTest
 */
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(
        named = "VERIF_DB_URL", matches = ".+")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("verif")
@org.springframework.context.annotation.Import(FileDuMedecinSurBaseTest.Auditeur.class)
class FileDuMedecinSurBaseTest {

    /**
     * L'auditeur que la tranche JPA n'apporte pas.
     *
     * <p>{@code @DataJpaTest} ne charge que la persistance ; l'auditeur, qui
     * vit dans la configuration de sécurité, en est absent alors que
     * {@code @EnableJpaAuditing} l'exige. Ce test ne persiste rien — un
     * auditeur vide suffit.</p>
     */
    @org.springframework.boot.test.context.TestConfiguration
    static class Auditeur {
        @org.springframework.context.annotation.Bean
        org.springframework.data.domain.AuditorAware<UUID> auditorAware() {
            return java.util.Optional::empty;
        }
    }

    @Autowired
    private TestOrderAssignmentDetailRepository depot;

    private void executer(FiltreFileDuMedecin filtre) {
        var tri = Sort.by("testOrderAssignment.date").ascending()
                .and(Sort.by("createdAt").ascending());
        depot.findAll(
                SpecificationFileDuMedecin.filtrer(
                        UUID.randomUUID(), LocalDate.now(), 18, filtre),
                PageRequest.of(0, 20, tri));
    }

    @Test
    @DisplayName("chaque filtre, seul, produit un SQL exécutable")
    void chaqueFiltreSeul() {
        assertThatCode(() -> {
            executer(FiltreFileDuMedecin.aucun());
            executer(new FiltreFileDuMedecin(2026, null, null, null, null, null, null, null));
            executer(new FiltreFileDuMedecin(null, "AF26-0001", null, null, null, null, null, null));
            executer(new FiltreFileDuMedecin(null, null, "a_traiter", null, null, null, null, null));
            executer(new FiltreFileDuMedecin(null, null, null, "VALIDATED", null, null, null, null));
            executer(new FiltreFileDuMedecin(null, null, null, null, true, null, null, null));
            executer(new FiltreFileDuMedecin(null, null, null, null, null, true, null, null));
            executer(new FiltreFileDuMedecin(null, null, null, null, null, null,
                    List.of(UUID.randomUUID()), null));
            // « À faire » n'est pas une égalité de statut mais un retrait :
            // c'est la position d'ouverture de l'écran, et elle a sa propre
            // forme SQL.
            executer(new FiltreFileDuMedecin(null, null, null, null, null, null,
                    null, true));
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("tous les filtres ensemble aussi")
    void tousEnsemble() {
        assertThatCode(() -> executer(new FiltreFileDuMedecin(
                2026, "AF26-0001", "a_traiter", "VALIDATED", true, true,
                List.of(UUID.randomUUID()), true))).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("une liste de demandes vide demande une page vide, pas une erreur")
    void listeVide() {
        // « IN () » est un SQL invalide : la spécification doit poser une
        // fausseté explicite plutôt que de laisser la liste vide s'écrire.
        assertThatCode(() -> executer(new FiltreFileDuMedecin(
                null, null, null, null, null, null, List.of(), null)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("le comptage du résumé passe par le même chemin")
    void comptage() {
        assertThatCode(() -> depot.count(SpecificationFileDuMedecin.filtrer(
                UUID.randomUUID(), LocalDate.now(), 18,
                new FiltreFileDuMedecin(2026, null, "termine", null, true, true, null, null))))
                .doesNotThrowAnyException();
    }
}

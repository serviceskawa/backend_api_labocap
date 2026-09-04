package com.labo.anapath.testorder;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Ce que « les urgents » doit désigner.
 *
 * <h2>Le compteur que plus rien ne faisait baisser</h2>
 *
 * <p>Le compteur « Cas urgent » du web interrogeait {@code isUrgent = true} et
 * rien d'autre. Il affichait 150 dossiers, dont 93 déjà remis : une urgence
 * satisfaite y restait pour toujours. Une fois les remises écartées il en
 * restait 57 — mais 54 d'entre eux dataient d'années passées, et noyaient les
 * trois de l'année en cours.</p>
 *
 * <h2>Quelle année, mesurée où</h2>
 *
 * <p>Sur la date de création. Le code porte pourtant l'année — « 26-0155 » —
 * mais il n'est attribué qu'à la validation : les 332 demandes en attente n'en
 * ont aucune, et un filtre sur le code les écartait toutes. Sur les 19 024
 * dossiers qui ont un code, aucun ne contredit son année de création : les deux
 * repères s'accordent, et celui-ci parle aussi des dossiers sans nom.</p>
 *
 * <p>Ni sur la date de prélèvement : un prélèvement de décembre enregistré en
 * janvier relève de la charge de l'année nouvelle, et 244 dossiers de
 * production sont dans ce cas.</p>
 */
class UrgentsDeLanneeTest {

    /** Les bornes et les colonnes qu'une spécification met en jeu. */
    private static List<String> predicatsPoses(TestOrderFilterDto filtre) {
        @SuppressWarnings("unchecked")
        Root<TestOrder> root = mock(Root.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        var query = mock(jakarta.persistence.criteria.CriteriaQuery.class);

        List<String> traces = new ArrayList<>();
        when(root.get(anyString())).thenAnswer(i -> {
            traces.add("get:" + i.getArgument(0));
            return mock(Path.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        });
        when(cb.greaterThanOrEqualTo(any(), any(LocalDateTime.class)))
                .thenAnswer(i -> {
                    traces.add("depuis:" + i.getArgument(1));
                    return mock(Predicate.class);
                });
        when(cb.lessThan(any(), any(LocalDateTime.class))).thenAnswer(i -> {
            traces.add("avant:" + i.getArgument(1));
            return mock(Predicate.class);
        });

        TestOrderSpecification.filter(UUID.randomUUID(), filtre).toPredicate(root, query, cb);
        return traces;
    }

    @Test
    @DisplayName("l'année devient un encadrement de la date de création")
    void anneeVersEncadrement() {
        TestOrderFilterDto f = new TestOrderFilterDto();
        f.setAnnee(2026);

        // Un encadrement et non un date_part : une fonction posée sur la
        // colonne écarterait tout index, sur une table de vingt mille lignes
        // interrogée à chaque ouverture d'écran.
        assertThat(predicatsPoses(f))
                .contains("depuis:2026-01-01T00:00", "avant:2027-01-01T00:00");
    }

    @Test
    @DisplayName("la borne haute exclut le 1er janvier suivant")
    void borneHauteStricte() {
        TestOrderFilterDto f = new TestOrderFilterDto();
        f.setAnnee(2025);

        // Stricte : un dossier ouvert le 1er janvier 2026 à 00h00 appartient à
        // 2026. Une borne inclusive le compterait dans les deux années.
        assertThat(predicatsPoses(f)).contains("avant:2026-01-01T00:00");
    }

    @Test
    @DisplayName("sans année demandée, aucune borne de date")
    void sansAnnee() {
        assertThat(predicatsPoses(new TestOrderFilterDto()))
                .noneMatch(t -> t.startsWith("depuis:") || t.startsWith("avant:"));
    }

    @Test
    @DisplayName("« en cours » écarte les dossiers remis et annulés")
    void enCoursEcarteRemisEtAnnules() {
        TestOrderFilterDto f = new TestOrderFilterDto();
        f.setEnCours(true);

        assertThat(predicatsPoses(f)).contains("get:status");
    }

    @Test
    @DisplayName("« en cours » à faux ne restreint rien")
    void enCoursFauxNeFiltrePas() {
        // Un booléen à faux dit « peu importe », pas « seulement les remis » :
        // le traiter comme une négation renverrait exactement la liste que
        // l'écran cherche à cacher.
        TestOrderFilterDto f = new TestOrderFilterDto();
        f.setEnCours(false);

        assertThat(predicatsPoses(f)).doesNotContain("get:status");
    }
}

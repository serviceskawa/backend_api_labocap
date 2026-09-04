package com.labo.anapath.testorder;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
 * <h2>L'année du code, pas celle du prélèvement</h2>
 *
 * <p>Un prélèvement de décembre enregistré en janvier porte un code de la
 * nouvelle année. C'est un dossier courant, et filtrer sur la date de
 * prélèvement l'écarterait — chaque mois de janvier, sur les dossiers les plus
 * récents.</p>
 */
class UrgentsDeLanneeTest {

    /** Les prédicats qu'une spécification pose, sans base de données. */
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
        when(cb.like(any(), anyString())).thenAnswer(i -> {
            traces.add("like:" + i.getArgument(1));
            return mock(Predicate.class);
        });

        TestOrderSpecification.filter(UUID.randomUUID(), filtre).toPredicate(root, query, cb);
        return traces;
    }

    @Test
    @DisplayName("l'année devient un préfixe de code à deux chiffres")
    void anneeVersPrefixe() {
        TestOrderFilterDto f = new TestOrderFilterDto();
        f.setAnnee(2026);

        assertThat(predicatsPoses(f)).contains("like:26-%");
    }

    @Test
    @DisplayName("une année du siècle précédent garde ses deux chiffres")
    void anneeSurDeuxChiffres() {
        TestOrderFilterDto f = new TestOrderFilterDto();
        // 2003 doit donner « 03- » et non « 3- » : sans le zéro, le motif ne
        // correspondrait à aucun code.
        f.setAnnee(2003);

        assertThat(predicatsPoses(f)).contains("like:03-%");
    }

    @Test
    @DisplayName("sans année demandée, aucun filtre sur le code")
    void sansAnnee() {
        assertThat(predicatsPoses(new TestOrderFilterDto()))
                .noneMatch(t -> t.startsWith("like:"));
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

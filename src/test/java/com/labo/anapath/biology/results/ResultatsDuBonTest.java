package com.labo.anapath.biology.results;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Garde de retrait et alignement des analyses à saisir d'un bon.
 */
class ResultatsDuBonTest {

    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final BiologyAnalysisResultRepository repo = mock(BiologyAnalysisResultRepository.class);
    private final AvancementDuCompteRendu avancement = mock(AvancementDuCompteRendu.class);
    private final ResultatsDuBon resultats = new ResultatsDuBon(repo, avancement);

    private BiologyAnalysisResult ligne(UUID labTestId) {
        BiologyAnalysisResult r = new BiologyAnalysisResult();
        r.setId(UUID.randomUUID());
        r.setTestOrderId(ORDER);
        r.setLabTestId(labTestId);
        return r;
    }

    @Test
    @DisplayName("garde : renvoie les analyses saisies ; liste vide → aucune requête")
    void garde() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(repo.findLabTestIdsWithResults(ORDER, List.of(a, b))).thenReturn(List.of(b));
        assertThat(resultats.analysesAvecResultats(ORDER, List.of(a, b))).containsExactly(b);
        assertThat(resultats.analysesAvecResultats(ORDER, List.of())).isEmpty();
        verify(repo, times(1)).findLabTestIdsWithResults(any(), anyCollection());
    }

    @Test
    @DisplayName("validation : une ligne PENDING par analyse, doublons et null ignorés, puis recalcul")
    void creation() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(repo.findByTestOrderId(ORDER)).thenReturn(List.of());
        List<UUID> demandees = new ArrayList<>(List.of(a, b, a));
        demandees.add(null);

        resultats.aligner(ORDER, BRANCH, demandees, USER);

        ArgumentCaptor<BiologyAnalysisResult> c = ArgumentCaptor.forClass(BiologyAnalysisResult.class);
        verify(repo, times(2)).save(c.capture());
        assertThat(c.getAllValues()).extracting(BiologyAnalysisResult::getLabTestId).containsExactly(a, b);
        assertThat(c.getAllValues()).allSatisfy(r -> {
            assertThat(r.getStatus()).isEqualTo(BiologyAnalysisStatus.PENDING);
            assertThat(r.getTestOrderId()).isEqualTo(ORDER);
            assertThat(r.getBranchId()).isEqualTo(BRANCH);
        });
        verify(avancement).recalculer(ORDER, BRANCH, USER);
    }

    @Test
    @DisplayName("rejouable : les analyses déjà présentes ne sont pas recréées")
    void idempotent() {
        UUID a = UUID.randomUUID();
        when(repo.findByTestOrderId(ORDER)).thenReturn(List.of(ligne(a)));

        resultats.aligner(ORDER, BRANCH, List.of(a), USER);

        verify(repo, never()).save(any());
        verify(repo, never()).delete(any());
    }

    @Test
    @DisplayName("analyse retirée sans résultat : supprimée ; avec résultats : conservée")
    void retrait() {
        UUID garde = UUID.randomUUID();
        UUID vierge = UUID.randomUUID();
        UUID saisie = UUID.randomUUID();
        BiologyAnalysisResult lVierge = ligne(vierge);
        BiologyAnalysisResult lSaisie = ligne(saisie);
        when(repo.findByTestOrderId(ORDER)).thenReturn(List.of(ligne(garde), lVierge, lSaisie));
        when(repo.findLabTestIdsWithResults(eq(ORDER), anyCollection())).thenReturn(List.of(saisie));

        resultats.aligner(ORDER, BRANCH, Set.of(garde), null);

        verify(repo).delete(lVierge);
        verify(repo, never()).delete(lSaisie);
        verify(avancement).recalculer(ORDER, BRANCH, null);
    }

    @Test
    @DisplayName("garde sans bon : vide, sans requête")
    void gardeSansBon() {
        assertThat(resultats.analysesAvecResultats(null, List.of(UUID.randomUUID()))).isEmpty();
        verifyNoInteractions(repo);
    }
}

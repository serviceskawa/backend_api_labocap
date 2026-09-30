package com.labo.anapath.biology.results;

import com.labo.anapath.report.LogReport;
import com.labo.anapath.report.LogReportRepository;
import com.labo.anapath.report.Report;
import com.labo.anapath.report.ReportRepository;
import com.labo.anapath.report.ReportStatus;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.labo.anapath.biology.results.BiologyAnalysisStatus.ENTERED;
import static com.labo.anapath.biology.results.BiologyAnalysisStatus.PENDING;
import static com.labo.anapath.biology.results.BiologyAnalysisStatus.TECH_VALIDATED;
import static com.labo.anapath.biology.results.BiologyValidationMode.ONE_STEP;
import static com.labo.anapath.biology.results.BiologyValidationMode.TWO_STEP;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le compte-rendu suit ses analyses : DRAFT ↔ PENDING_REVIEW selon le mode.
 */
class AvancementDuCompteRenduTest {

    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID BRANCH = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final BiologyAnalysisResultRepository lignes = mock(BiologyAnalysisResultRepository.class);
    private final ReportRepository reports = mock(ReportRepository.class);
    private final LogReportRepository journal = mock(LogReportRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ReglagesDeBiologie reglages = mock(ReglagesDeBiologie.class);
    private final AvancementDuCompteRendu avancement =
            new AvancementDuCompteRendu(lignes, reports, journal, users, reglages);
    private Report report;

    @BeforeEach
    void setUp() {
        report = new Report();
        report.setId(UUID.randomUUID());
        report.setStatus(ReportStatus.DRAFT);
        when(reports.findByTestOrderId(ORDER)).thenReturn(Optional.of(report));
        when(users.findById(USER)).thenReturn(Optional.of(new User()));
        when(reglages.mode(BRANCH)).thenReturn(TWO_STEP);
    }

    private void analyses(BiologyAnalysisStatus... statuts) {
        List<BiologyAnalysisResult> l = new ArrayList<>();
        for (BiologyAnalysisStatus s : statuts) {
            BiologyAnalysisResult r = new BiologyAnalysisResult();
            r.setStatus(s);
            l.add(r);
        }
        when(lignes.findByTestOrderId(ORDER)).thenReturn(l);
    }

    @Test
    @DisplayName("prêt : TWO_STEP exige tout TECH_VALIDATED, ONE_STEP accepte ENTERED ; jamais sans analyse")
    void pret() {
        assertThat(AvancementDuCompteRendu.pret(List.of(TECH_VALIDATED, TECH_VALIDATED), TWO_STEP)).isTrue();
        assertThat(AvancementDuCompteRendu.pret(List.of(TECH_VALIDATED, ENTERED), TWO_STEP)).isFalse();
        assertThat(AvancementDuCompteRendu.pret(List.of(TECH_VALIDATED, ENTERED), ONE_STEP)).isTrue();
        assertThat(AvancementDuCompteRendu.pret(List.of(ENTERED, PENDING), ONE_STEP)).isFalse();
        assertThat(AvancementDuCompteRendu.pret(List.of(), ONE_STEP)).isFalse();
        assertThat(AvancementDuCompteRendu.pret(null, TWO_STEP)).isFalse();
    }

    @Test
    @DisplayName("TWO_STEP, tout validé techniquement : DRAFT → PENDING_REVIEW, journalisé")
    void deuxEtapes_pret() {
        analyses(TECH_VALIDATED, TECH_VALIDATED);
        avancement.recalculer(ORDER, BRANCH, USER);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING_REVIEW);
        ArgumentCaptor<LogReport> log = ArgumentCaptor.forClass(LogReport.class);
        verify(journal).save(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo(AvancementDuCompteRendu.ACTION_PRET);
        assertThat(log.getValue().getReport()).isSameAs(report);
    }

    @Test
    @DisplayName("TWO_STEP, une analyse seulement saisie : reste DRAFT")
    void deuxEtapes_pasPret() {
        analyses(TECH_VALIDATED, ENTERED);
        avancement.recalculer(ORDER, BRANCH, USER);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);
        verify(reports, never()).save(any());
        verify(journal, never()).save(any());
    }

    @Test
    @DisplayName("ONE_STEP, tout saisi : DRAFT → PENDING_REVIEW")
    void uneEtape_pret() {
        when(reglages.mode(BRANCH)).thenReturn(ONE_STEP);
        analyses(ENTERED, TECH_VALIDATED);
        avancement.recalculer(ORDER, BRANCH, USER);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("une analyse n'est plus prête : PENDING_REVIEW → DRAFT, journalisé")
    void retour() {
        report.setStatus(ReportStatus.PENDING_REVIEW);
        analyses(TECH_VALIDATED, ENTERED);
        avancement.recalculer(ORDER, BRANCH, USER);
        assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);
        ArgumentCaptor<LogReport> log = ArgumentCaptor.forClass(LogReport.class);
        verify(journal).save(log.capture());
        assertThat(log.getValue().getAction()).isEqualTo(AvancementDuCompteRendu.ACTION_RETOUR);
    }

    @Test
    @DisplayName("compte-rendu validé ou livré : jamais touché")
    void valideIntouchable() {
        for (ReportStatus s : new ReportStatus[]{ReportStatus.VALIDATED, ReportStatus.DELIVERED}) {
            report.setStatus(s);
            analyses(ENTERED);
            avancement.recalculer(ORDER, BRANCH, USER);
            assertThat(report.getStatus()).isEqualTo(s);
        }
        verify(reports, never()).save(any());
    }

    @Test
    @DisplayName("sans compte-rendu : rien")
    void sansCompteRendu() {
        when(reports.findByTestOrderId(ORDER)).thenReturn(Optional.empty());
        avancement.recalculer(ORDER, BRANCH, USER);
        verify(lignes, never()).findByTestOrderId(any());
    }

    @Test
    @DisplayName("journal sans auteur connu : rien d'écrit (la colonne user_id est obligatoire)")
    void journalSansAuteur() {
        avancement.journaliser(report, null, "X", "y");
        verify(journal, never()).save(any());
    }
}

package com.labo.anapath.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RattrapageAvisPatientJobTest {

    @Mock private ReportRepository reportRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private RattrapageAvisPatientJob job;

    private Report compteRendu() {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        return report;
    }

    @Test
    @DisplayName("chaque avis en attente est envoyé, et le lot est plafonné à 100")
    void envoieLesAvisEnAttente() {
        Report premier = compteRendu();
        Report second = compteRendu();
        when(reportRepository.findAvisPatientEnAttente(any(), any()))
                .thenReturn(List.of(premier, second));
        when(notificationService.notifyPatient(any(), eq(null)))
                .thenReturn(new NotifyResponseDto("CALL", null, "appel-1", "Appel vocal lancé"));

        assertThat(job.rattraperAvisEnAttente()).isEqualTo(2);

        verify(notificationService).notifyPatient(premier.getId(), null);
        verify(notificationService).notifyPatient(second.getId(), null);

        ArgumentCaptor<Pageable> lot = ArgumentCaptor.forClass(Pageable.class);
        verify(reportRepository).findAvisPatientEnAttente(any(), lot.capture());
        assertThat(lot.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("la reprise ne remonte pas au-delà de trois jours")
    void borneLaFenetreATroisJours() {
        when(reportRepository.findAvisPatientEnAttente(any(), any())).thenReturn(List.of());

        job.rattraperAvisEnAttente();

        // Sans cette borne, une panne de plusieurs jours ferait partir un arriéré
        // entier d'appels à la première exécution.
        ArgumentCaptor<LocalDateTime> depuis = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(reportRepository).findAvisPatientEnAttente(depuis.capture(), any());
        assertThat(depuis.getValue())
                .isBetween(LocalDateTime.now().minusDays(3).minusMinutes(1),
                           LocalDateTime.now().minusDays(3).plusMinutes(1));
    }

    @Test
    @DisplayName("un avis en échec n'empêche pas les suivants de partir")
    void poursuitLeLotMalgreUnEchec() {
        Report enEchec = compteRendu();
        Report suivant = compteRendu();
        when(reportRepository.findAvisPatientEnAttente(any(), any()))
                .thenReturn(List.of(enEchec, suivant));
        when(notificationService.notifyPatient(enEchec.getId(), null))
                .thenThrow(new com.labo.anapath.common.exception.InvalidOperationException("OurVoice injoignable"));
        when(notificationService.notifyPatient(suivant.getId(), null))
                .thenReturn(new NotifyResponseDto("CALL", null, "appel-2", "Appel vocal lancé"));

        assertThat(job.rattraperAvisEnAttente()).isEqualTo(1);
        verify(notificationService).notifyPatient(suivant.getId(), null);
    }

    @Test
    @DisplayName("un compte-rendu resté hors plage n'est pas compté comme envoyé")
    void neComptePasLesAvisNonPartis() {
        Report report = compteRendu();
        when(reportRepository.findAvisPatientEnAttente(any(), any())).thenReturn(List.of(report));
        when(notificationService.notifyPatient(any(), eq(null)))
                .thenReturn(new NotifyResponseDto("NONE", null, null, "Hors plage"));

        assertThat(job.rattraperAvisEnAttente()).isZero();
    }
}

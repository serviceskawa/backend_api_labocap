package com.labo.anapath.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.labo.anapath.user.UserRepository;

/**
 * Bornes de la fenêtre servant au calcul de fermeture.
 *
 * <p>Chaque session doit couvrir l'intervalle qui la sépare de la suivante. Sans
 * borne haute, une session restée ouverte compte l'argent encaissé après
 * l'ouverture de la suivante — que la fermeture de celle-ci comptera une seconde
 * fois. C'est ce double comptage que ces tests interdisent.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FenetreFermetureCaisseTest {

    @Mock private CashboxDailyRepository cashboxDailyRepository;
    @Mock private CashboxRepository cashboxRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private CashboxDailyServiceImpl service;

    private final UUID BRANCHE = UUID.randomUUID();
    private final UUID SESSION = UUID.randomUUID();

    private CashboxDaily session(LocalDateTime ouverture) {
        CashboxDaily d = new CashboxDaily();
        d.setId(SESSION);
        d.setBranchId(BRANCHE);
        d.setCreatedAt(ouverture);
        d.setUpdatedAt(ouverture);
        return d;
    }

    private LocalDateTime borneHauteRetenue() {
        ArgumentCaptor<LocalDateTime> depuis = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> jusqua = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(cashboxDailyRepository, org.mockito.Mockito.atLeastOnce())
                .sumCreditByPaymentMethod(eq(BRANCHE), any(), depuis.capture(), jusqua.capture());
        return jusqua.getValue();
    }

    @Test
    @DisplayName("Une session suivie d'une autre s'arrête à l'ouverture de celle-ci")
    void borneeParLaSessionSuivante() {
        LocalDateTime ouverture = LocalDateTime.of(2026, 8, 7, 8, 45);
        LocalDateTime suivante = LocalDateTime.of(2026, 8, 10, 7, 32);

        CashboxDaily apres = new CashboxDaily();
        apres.setBranchId(BRANCHE);
        apres.setCreatedAt(suivante);
        apres.setUpdatedAt(suivante);

        when(cashboxDailyRepository.findById(SESSION)).thenReturn(Optional.of(session(ouverture)));
        when(cashboxDailyRepository.findFirstByBranchIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(
                BRANCHE, ouverture)).thenReturn(Optional.of(apres));
        when(cashboxDailyRepository.sumCreditByPaymentMethod(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        service.getDailySummary(BRANCHE, SESSION);

        assertThat(borneHauteRetenue()).isEqualTo(suivante);
    }

    /**
     * Le cas courant : on ferme la dernière session ouverte. La fenêtre reste
     * ouverte, sans quoi les règlements de la journée en cours seraient exclus.
     */
    @Test
    @DisplayName("La dernière session n'a pas de borne haute")
    void derniereSessionSansBorne() {
        LocalDateTime ouverture = LocalDateTime.of(2026, 8, 13, 8, 0);

        when(cashboxDailyRepository.findById(SESSION)).thenReturn(Optional.of(session(ouverture)));
        when(cashboxDailyRepository.findFirstByBranchIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(
                BRANCHE, ouverture)).thenReturn(Optional.empty());
        when(cashboxDailyRepository.sumCreditByPaymentMethod(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        service.getDailySummary(BRANCHE, SESSION);

        assertThat(borneHauteRetenue()).isNull();
    }

    @Test
    @DisplayName("Nombres et montants partagent exactement la même fenêtre")
    void memeFenetrePourLesNombres() {
        LocalDateTime ouverture = LocalDateTime.of(2026, 8, 7, 8, 45);
        LocalDateTime suivante = LocalDateTime.of(2026, 8, 10, 7, 32);

        CashboxDaily apres = new CashboxDaily();
        apres.setBranchId(BRANCHE);
        apres.setCreatedAt(suivante);
        apres.setUpdatedAt(suivante);

        when(cashboxDailyRepository.findById(SESSION)).thenReturn(Optional.of(session(ouverture)));
        when(cashboxDailyRepository.findFirstByBranchIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(
                BRANCHE, ouverture)).thenReturn(Optional.of(apres));
        when(cashboxDailyRepository.sumCreditByPaymentMethod(any(), any(), any(), any()))
                .thenReturn(BigDecimal.ZERO);

        service.getDailySummary(BRANCHE, SESSION);

        ArgumentCaptor<LocalDateTime> depuis = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> jusqua = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(cashboxDailyRepository, org.mockito.Mockito.atLeastOnce())
                .countCreditByPaymentMethod(eq(BRANCHE), any(), depuis.capture(), jusqua.capture());

        assertThat(depuis.getValue()).isEqualTo(ouverture);
        assertThat(jusqua.getValue()).isEqualTo(suivante);
    }
}

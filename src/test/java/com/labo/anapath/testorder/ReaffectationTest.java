package com.labo.anapath.testorder;

import com.labo.anapath.branch.BranchRepository;
import com.labo.anapath.common.exception.ReaffectationNonConfirmeeException;
import com.labo.anapath.report.TestPathologyMacro;
import com.labo.anapath.report.TestPathologyMacroRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Confier à un second médecin un dossier qu'un premier détient.
 *
 * <h2>Ce que le code faisait avant</h2>
 *
 * <p>Rien. Une demande déjà affectée était détectée, et la seconde affectation
 * était abandonnée en silence : la ligne restait attachée au premier médecin,
 * pendant que l'écran annonçait l'ajout. L'utilisateur croyait avoir transféré
 * le dossier ; le dossier n'avait pas bougé, et rien nulle part ne le disait.</p>
 *
 * <h2>Ce qu'on exige maintenant</h2>
 *
 * <p>Le transfert a lieu, mais seulement s'il est confirmé — un dossier ne
 * change pas de mains par inadvertance. Et la ligne précédente survit, datée :
 * c'est elle qui répond plus tard à « à qui l'avait-on confié d'abord ».</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReaffectationTest {

    @Mock private TestOrderAssignmentRepository assignmentRepository;
    @Mock private TestOrderAssignmentDetailRepository detailRepository;
    @Mock private TestOrderRepository testOrderRepository;
    @Mock private UserRepository userRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private TestPathologyMacroRepository macroRepository;

    @InjectMocks private TestOrderAssignmentServiceImpl service;

    private final UUID BRANCHE = UUID.randomUUID();
    private final UUID DEMANDE = UUID.randomUUID();
    private final UUID LOT_INITIAL = UUID.randomUUID();
    private final UUID LOT_NOUVEAU = UUID.randomUUID();

    private User medecin(String nom) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setFirstname("Florence");
        u.setLastname(nom);
        return u;
    }

    private TestOrderAssignment lot(UUID id, String code, User titulaire) {
        TestOrderAssignment a = new TestOrderAssignment();
        a.setId(id);
        a.setBranchId(BRANCHE);
        a.setCode(code);
        a.setDate(LocalDate.now());
        a.setUser(titulaire);
        a.setDetails(new ArrayList<>());
        return a;
    }

    private TestOrder demande() {
        TestOrder o = new TestOrder();
        o.setId(DEMANDE);
        o.setBranchId(BRANCHE);
        o.setCode("26-0155");
        o.setStatus(TestOrderStatus.VALIDATED);
        return o;
    }

    private TestOrderAssignmentDetail ligne(TestOrderAssignment lot, TestOrder demande) {
        TestOrderAssignmentDetail d = new TestOrderAssignmentDetail();
        d.setId(UUID.randomUUID());
        d.setBranchId(BRANCHE);
        d.setTestOrderAssignment(lot);
        d.setTestOrder(demande);
        d.setTestOrderCode(demande.getCode());
        return d;
    }

    private AssignmentDetailRequestDto requete(boolean confirmee) {
        AssignmentDetailRequestDto dto = new AssignmentDetailRequestDto();
        dto.setTestOrderId(DEMANDE);
        dto.setConfirmerReaffectation(confirmee);
        return dto;
    }

    private void monterLeDecor(TestOrderAssignment nouveauLot,
                               Optional<TestOrderAssignmentDetail> courante) {
        when(assignmentRepository.findById(nouveauLot.getId()))
                .thenReturn(Optional.of(nouveauLot));
        when(testOrderRepository.findById(DEMANDE)).thenReturn(Optional.of(demande()));
        when(detailRepository.findByTestOrderId(DEMANDE)).thenReturn(courante);
        when(detailRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(macroRepository.findByTestOrderId(any())).thenReturn(Optional.empty());
        when(macroRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    @DisplayName("sans confirmation, le transfert est refusé et nomme le médecin en place")
    void refuseSansConfirmation() {
        TestOrder d = demande();
        TestOrderAssignment initial = lot(LOT_INITIAL, "AF26-0001", medecin("HOUNNOU"));
        TestOrderAssignment nouveau = lot(LOT_NOUVEAU, "AF26-0002", medecin("ADECHINA"));
        monterLeDecor(nouveau, Optional.of(ligne(initial, d)));

        assertThatThrownBy(() -> service.addDetail(LOT_NOUVEAU, requete(false)))
                .isInstanceOf(ReaffectationNonConfirmeeException.class)
                // Le message doit porter les deux repères sur lesquels on décide :
                // quel dossier, et chez qui il est. Un « conflit » nu ferait
                // confirmer sans savoir ce qu'on retire à qui.
                .hasMessageContaining("26-0155")
                .hasMessageContaining("HOUNNOU");

        verify(detailRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmé, le dossier change de mains et l'ancienne ligne est datée")
    void transfereEtDateLAncienne() {
        TestOrder d = demande();
        TestOrderAssignment initial = lot(LOT_INITIAL, "AF26-0001", medecin("HOUNNOU"));
        TestOrderAssignment nouveau = lot(LOT_NOUVEAU, "AF26-0002", medecin("ADECHINA"));
        TestOrderAssignmentDetail ancienne = ligne(initial, d);
        monterLeDecor(nouveau, Optional.of(ancienne));

        service.addDetail(LOT_NOUVEAU, requete(true));

        assertThat(ancienne.getRemplaceeLe()).isNotNull();
        assertThat(ancienne.estCourante()).isFalse();

        var enregistrees = org.mockito.ArgumentCaptor
                .forClass(TestOrderAssignmentDetail.class);
        verify(detailRepository, org.mockito.Mockito.times(2)).save(enregistrees.capture());
        TestOrderAssignmentDetail nouvelle = enregistrees.getAllValues().get(1);
        assertThat(nouvelle.getTestOrderAssignment()).isSameAs(nouveau);
        // Le nouveau médecin reprend à zéro : hériter du « pris en charge » de
        // son prédécesseur lui ferait croire qu'il a déjà ouvert le dossier.
        assertThat(nouvelle.statutDuMedecin()).isEqualTo(DocteurStatus.A_TRAITER);
        assertThat(nouvelle.estCourante()).isTrue();
    }

    @Test
    @DisplayName("une demande libre s'affecte sans rien demander")
    void premiereAffectationSansConfirmation() {
        TestOrderAssignment nouveau = lot(LOT_NOUVEAU, "AF26-0002", medecin("ADECHINA"));
        monterLeDecor(nouveau, Optional.empty());

        service.addDetail(LOT_NOUVEAU, requete(false));

        verify(detailRepository, org.mockito.Mockito.times(1)).save(any());
    }

    @Test
    @DisplayName("l'historique rend les prises en charge dans l'ordre, la courante en dernier")
    void historiqueOrdonne() {
        TestOrder d = demande();
        TestOrderAssignment initial = lot(LOT_INITIAL, "AF26-0001", medecin("HOUNNOU"));
        TestOrderAssignment nouveau = lot(LOT_NOUVEAU, "AF26-0002", medecin("ADECHINA"));
        TestOrderAssignmentDetail premiere = ligne(initial, d);
        premiere.setRemplaceeLe(java.time.LocalDateTime.now());
        TestOrderAssignmentDetail seconde = ligne(nouveau, d);

        when(testOrderRepository.findByIdAndBranchId(DEMANDE, BRANCHE))
                .thenReturn(Optional.of(d));
        when(detailRepository.historiqueDe(DEMANDE))
                .thenReturn(List.of(premiere, seconde));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        HistoriqueAffectationDto historique = service.historiqueDe(DEMANDE, BRANCHE);

        assertThat(historique.code()).isEqualTo("26-0155");
        assertThat(historique.etapes()).hasSize(2);
        assertThat(historique.etapes().get(0).medecin()).isEqualTo("HOUNNOU Florence");
        assertThat(historique.etapes().get(0).courante()).isFalse();
        assertThat(historique.etapes().get(1).medecin()).isEqualTo("ADECHINA Florence");
        assertThat(historique.etapes().get(1).courante()).isTrue();
    }

    @Test
    @DisplayName("une demande jamais affectée a un historique vide, pas une erreur")
    void historiqueVide() {
        TestOrder d = demande();
        when(testOrderRepository.findByIdAndBranchId(DEMANDE, BRANCHE))
                .thenReturn(Optional.of(d));
        when(detailRepository.historiqueDe(DEMANDE)).thenReturn(List.of());

        assertThat(service.historiqueDe(DEMANDE, BRANCHE).etapes()).isEmpty();
    }

    /** Une macro est créée à chaque affectation ; le test ne s'y intéresse pas. */
    @SuppressWarnings("unused")
    private TestPathologyMacro ignoree;
}

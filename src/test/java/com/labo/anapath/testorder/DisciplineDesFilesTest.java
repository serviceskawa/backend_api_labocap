package com.labo.anapath.testorder;

import com.labo.anapath.common.Discipline;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.report.TestPathologyMacroRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Les listes et files d'anatomie pathologique ne voient que l'anatomie
 * pathologique, tant qu'on ne leur demande pas autre chose.
 *
 * <p>Avant qu'aucun bon de biologie n'existe, chaque liste existante doit
 * filtrer sur {@link Discipline#PATHOLOGY} par défaut : les écrans web et les
 * applications mobiles déjà installées n'envoient pas ce critère, et doivent
 * continuer de voir exactement ce qu'ils voyaient.</p>
 */
class DisciplineDesFilesTest {

    /**
     * Les valeurs de discipline que la spécification compare, dans l'ordre où
     * elle les pose.
     */
    private static List<Object> disciplinesComparees(CriteriaBuilder cb) {
        List<Object> valeurs = new ArrayList<>();
        when(cb.equal(any(Expression.class), any(Object.class))).thenAnswer(i -> {
            if (i.getArgument(1) instanceof Discipline d) {
                valeurs.add(d);
            }
            return mock(Predicate.class);
        });
        return valeurs;
    }

    @Nested
    @DisplayName("Liste des demandes (TestOrderSpecification)")
    class ListeDesDemandes {

        @SuppressWarnings("unchecked")
        private List<Object> appliquer(TestOrderFilterDto filtre) {
            Root<TestOrder> root = mock(Root.class, RETURNS_DEEP_STUBS);
            CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
            CriteriaQuery<?> query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
            List<Object> valeurs = disciplinesComparees(cb);
            TestOrderSpecification.filter(UUID.randomUUID(), filtre).toPredicate(root, query, cb);
            return valeurs;
        }

        @Test
        @DisplayName("un filtre neuf vise l'anatomie pathologique")
        void filtreNeufPathologie() {
            assertThat(new TestOrderFilterDto().getDiscipline()).isEqualTo(Discipline.PATHOLOGY);
            assertThat(appliquer(new TestOrderFilterDto())).containsExactly(Discipline.PATHOLOGY);
        }

        @Test
        @DisplayName("la discipline demandée devient un prédicat")
        void disciplineDemandee() {
            TestOrderFilterDto f = new TestOrderFilterDto();
            f.setDiscipline(Discipline.BIOLOGY);
            assertThat(appliquer(f)).containsExactly(Discipline.BIOLOGY);
        }

        @Test
        @DisplayName("une discipline nulle ne filtre rien — réservé aux appelants internes")
        void disciplineNulle() {
            TestOrderFilterDto f = new TestOrderFilterDto();
            f.setDiscipline(null);
            assertThat(appliquer(f)).isEmpty();
        }

        @Test
        @DisplayName("la file de macroscopie est figée sur l'anatomie pathologique")
        @SuppressWarnings("unchecked")
        void fileDeMacroscopie() {
            Root<TestOrder> root = mock(Root.class, RETURNS_DEEP_STUBS);
            CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
            CriteriaQuery<?> query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
            List<Object> valeurs = disciplinesComparees(cb);

            TestOrderSpecification.macroscopieEnAttente(UUID.randomUUID()).toPredicate(root, query, cb);

            assertThat(valeurs).containsExactly(Discipline.PATHOLOGY);
        }
    }

    @Nested
    @DisplayName("File du médecin (SpecificationFileDuMedecin)")
    class FileDuMedecin {

        @SuppressWarnings("unchecked")
        private List<Object> appliquer(FiltreFileDuMedecin filtre) {
            Root<TestOrderAssignmentDetail> racine = mock(Root.class, RETURNS_DEEP_STUBS);
            Join<Object, Object> demande = mock(Join.class, RETURNS_DEEP_STUBS);
            when(racine.join("testOrder", JoinType.LEFT)).thenAnswer(i -> demande);
            List<String> champsDeLaDemande = new ArrayList<>();
            when(demande.get(anyString())).thenAnswer(i -> {
                champsDeLaDemande.add(i.getArgument(0));
                return mock(Path.class, RETURNS_DEEP_STUBS);
            });
            CriteriaBuilder cb = mock(CriteriaBuilder.class, RETURNS_DEEP_STUBS);
            CriteriaQuery<?> query = mock(CriteriaQuery.class, RETURNS_DEEP_STUBS);
            List<Object> valeurs = disciplinesComparees(cb);

            SpecificationFileDuMedecin.filtrer(UUID.randomUUID(), LocalDate.now(), 18, filtre)
                    .toPredicate(racine, query, cb);

            assertThat(champsDeLaDemande).contains("discipline");
            return valeurs;
        }

        @Test
        @DisplayName("le filtre d'avant la biologie vise l'anatomie pathologique")
        void filtreHistorique() {
            FiltreFileDuMedecin f = new FiltreFileDuMedecin(
                    2026, null, null, null, null, null, null, null, null);
            assertThat(f.discipline()).isEqualTo(Discipline.PATHOLOGY);
            assertThat(FiltreFileDuMedecin.aucun().discipline()).isEqualTo(Discipline.PATHOLOGY);
            assertThat(appliquer(f)).containsExactly(Discipline.PATHOLOGY);
        }

        @Test
        @DisplayName("une discipline nulle retombe sur l'anatomie pathologique")
        void disciplineNulle() {
            FiltreFileDuMedecin f = new FiltreFileDuMedecin(
                    null, null, null, null, null, null, null, null, null, null);
            assertThat(f.discipline()).isEqualTo(Discipline.PATHOLOGY);
        }

        @Test
        @DisplayName("les dérivations conservent la discipline")
        void derivations() {
            FiltreFileDuMedecin f = new FiltreFileDuMedecin(
                    null, null, null, null, null, null, null, null, null, Discipline.BIOLOGY);
            assertThat(f.avecAvancement(Avancement.TERMINE).discipline()).isEqualTo(Discipline.BIOLOGY);
            assertThat(f.avecStatutDemande(TestOrderStatus.PENDING).discipline()).isEqualTo(Discipline.BIOLOGY);
            assertThat(f.avecAlerte(true, null).discipline()).isEqualTo(Discipline.BIOLOGY);
            assertThat(f.sansStatutDuMedecin().discipline()).isEqualTo(Discipline.BIOLOGY);
            assertThat(appliquer(f)).containsExactly(Discipline.BIOLOGY);
        }
    }

    @Nested
    @ExtendWith(MockitoExtension.class)
    @DisplayName("Composition d'un lot")
    class CompositionDUnLot {

        @Mock private TestOrderAssignmentRepository assignmentRepository;
        @Mock private TestOrderAssignmentDetailRepository detailRepository;
        @Mock private TestOrderRepository testOrderRepository;
        @Mock private TestPathologyMacroRepository macroRepository;

        @InjectMocks
        private TestOrderAssignmentServiceImpl service;

        private TestOrder bon(Discipline discipline, String code) {
            TestOrder o = new TestOrder();
            ReflectionTestUtils.setField(o, "id", UUID.randomUUID());
            o.setCode(code);
            o.setStatus(TestOrderStatus.VALIDATED);
            o.setDiscipline(discipline);
            return o;
        }

        private TestOrderAssignment lotContenant(TestOrder... bons) {
            TestOrderAssignment lot = new TestOrderAssignment();
            ReflectionTestUtils.setField(lot, "id", UUID.randomUUID());
            lot.setCode("AF26-0001");
            for (TestOrder b : bons) {
                TestOrderAssignmentDetail d = new TestOrderAssignmentDetail();
                d.setTestOrderAssignment(lot);
                d.setTestOrder(b);
                d.setTestOrderCode(b.getCode());
                lot.getDetails().add(d);
            }
            return lot;
        }

        private AssignmentDetailRequestDto demande(TestOrder b) {
            AssignmentDetailRequestDto dto = new AssignmentDetailRequestDto();
            dto.setTestOrderId(b.getId());
            return dto;
        }

        @Test
        @DisplayName("un bon de biologie est refusé dans un lot d'anatomie pathologique")
        void lotMixteRefuse() {
            TestOrder patho = bon(Discipline.PATHOLOGY, "26-0001");
            TestOrder bio = bon(Discipline.BIOLOGY, "26-0002");
            TestOrderAssignment lot = lotContenant(patho);
            when(assignmentRepository.findByIdAndBranchId(eq(lot.getId()), any())).thenReturn(Optional.of(lot));
            when(testOrderRepository.findByIdAndBranchId(eq(bio.getId()), any())).thenReturn(Optional.of(bio));

            assertThatThrownBy(() -> service.addDetail(lot.getId(), demande(bio)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("disciplines");
            verify(detailRepository, never()).save(any());
            verifyNoInteractions(macroRepository);
        }

        @Test
        @DisplayName("une ligne remplacée ne compte pas dans la discipline du lot")
        void ligneRemplaceeIgnoree() {
            TestOrder patho = bon(Discipline.PATHOLOGY, "26-0001");
            TestOrder bio = bon(Discipline.BIOLOGY, "26-0002");
            TestOrderAssignment lot = lotContenant(patho);
            lot.getDetails().get(0).setRemplaceeLe(java.time.LocalDateTime.now());
            when(assignmentRepository.findByIdAndBranchId(eq(lot.getId()), any())).thenReturn(Optional.of(lot));
            when(testOrderRepository.findByIdAndBranchId(eq(bio.getId()), any())).thenReturn(Optional.of(bio));

            service.addDetail(lot.getId(), demande(bio));

            verify(detailRepository).save(any());
        }

        @Test
        @DisplayName("un bon de biologie n'ouvre pas de macroscopie en entrant dans un lot")
        void biologieSansMacroscopie() {
            TestOrder bio = bon(Discipline.BIOLOGY, "26-0002");
            TestOrderAssignment lot = lotContenant();
            when(assignmentRepository.findByIdAndBranchId(eq(lot.getId()), any())).thenReturn(Optional.of(lot));
            when(testOrderRepository.findByIdAndBranchId(eq(bio.getId()), any())).thenReturn(Optional.of(bio));

            service.addDetail(lot.getId(), demande(bio));

            verify(detailRepository).save(any());
            verifyNoInteractions(macroRepository);
        }

        @Test
        @DisplayName("un bon d'anatomie pathologique rejoint un lot de même discipline")
        void memeDisciplineAccepte() {
            TestOrder patho1 = bon(Discipline.PATHOLOGY, "26-0001");
            TestOrder patho2 = bon(Discipline.PATHOLOGY, "26-0003");
            TestOrderAssignment lot = lotContenant(patho1);
            when(assignmentRepository.findByIdAndBranchId(eq(lot.getId()), any())).thenReturn(Optional.of(lot));
            when(testOrderRepository.findByIdAndBranchId(eq(patho2.getId()), any())).thenReturn(Optional.of(patho2));
            when(macroRepository.findByTestOrderId(patho2.getId())).thenReturn(Optional.empty());

            service.addDetail(lot.getId(), demande(patho2));

            verify(detailRepository).save(any());
            verify(macroRepository).save(any());
        }
    }
}

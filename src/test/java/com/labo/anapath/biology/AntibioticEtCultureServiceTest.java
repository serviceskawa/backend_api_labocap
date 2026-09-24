package com.labo.anapath.biology;

import com.labo.anapath.common.Discipline;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.DuplicateResourceException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.test.LabTest;
import com.labo.anapath.test.LabTestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Référentiels de la bactériologie : antibiotiques et options de culture.
 */
class AntibioticEtCultureServiceTest {

    private static final UUID BRANCH = UUID.randomUUID();

    @Nested
    @DisplayName("antibiotiques")
    class Antibiotiques {

        private final AntibioticRepository repository = mock(AntibioticRepository.class);
        private final AntibioticServiceImpl service = new AntibioticServiceImpl(repository);

        private AntibioticRequestDto requete(String nom, String code) {
            AntibioticRequestDto dto = new AntibioticRequestDto();
            dto.setName(nom);
            dto.setCode(code);
            return dto;
        }

        @BeforeEach
        void setUp() {
            when(repository.save(any())).then(returnsFirstArg());
        }

        @Test
        @DisplayName("création : champs rognés, vides ramenés à null, position 0 par défaut")
        void creation() {
            AntibioticRequestDto dto = requete("  Amoxicilline ", " ");
            dto.setFamily("Bêta-lactamines");
            dto.setCommercialName("");

            AntibioticResponseDto cree = service.create(dto, BRANCH);

            assertThat(cree.name()).isEqualTo("Amoxicilline");
            assertThat(cree.code()).isNull();
            assertThat(cree.commercialName()).isNull();
            assertThat(cree.position()).isZero();
            ArgumentCaptor<Antibiotic> a = ArgumentCaptor.forClass(Antibiotic.class);
            verify(repository).save(a.capture());
            assertThat(a.getValue().getBranchId()).isEqualTo(BRANCH);
        }

        @Test
        @DisplayName("nom ou code déjà pris : 409")
        void doublons() {
            when(repository.existsByNameIgnoreCaseAndBranchId("Amoxicilline", BRANCH)).thenReturn(true);
            assertThatThrownBy(() -> service.create(requete("Amoxicilline", null), BRANCH))
                    .isInstanceOf(DuplicateResourceException.class);

            when(repository.existsByCodeIgnoreCaseAndBranchId("AMX", BRANCH)).thenReturn(true);
            assertThatThrownBy(() -> service.create(requete("Autre", "AMX"), BRANCH))
                    .isInstanceOf(DuplicateResourceException.class).hasMessageContaining("AMX");
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("modification et suppression limitées à la succursale")
        void isolation() {
            UUID id = UUID.randomUUID();
            when(repository.findByIdAndBranchId(id, BRANCH)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(id, requete("X", null), BRANCH))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> service.delete(id, BRANCH))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("modification : le doublon s'apprécie hors de l'antibiotique lui-même")
        void modification() {
            Antibiotic a = new Antibiotic();
            a.setId(UUID.randomUUID());
            a.setName("Amoxicilline");
            when(repository.findByIdAndBranchId(a.getId(), BRANCH)).thenReturn(Optional.of(a));

            AntibioticRequestDto dto = requete("Amoxicilline", "AMX");
            dto.setPosition(3);
            service.update(a.getId(), dto, BRANCH);

            verify(repository).existsByNameIgnoreCaseAndBranchIdAndIdNot("Amoxicilline", BRANCH, a.getId());
            assertThat(a.getCode()).isEqualTo("AMX");
            assertThat(a.getPosition()).isEqualTo(3);
        }

        @Test
        @DisplayName("suppression logique")
        void suppression() {
            Antibiotic a = new Antibiotic();
            a.setId(UUID.randomUUID());
            when(repository.findByIdAndBranchId(a.getId(), BRANCH)).thenReturn(Optional.of(a));

            service.delete(a.getId(), BRANCH);

            verify(repository).delete(a);
        }
    }

    @Nested
    @DisplayName("options de culture")
    class OptionsDeCulture {

        private final BiologyCultureOptionRepository optionRepository = mock(BiologyCultureOptionRepository.class);
        private final LabTestCultureOptionRepository linkRepository = mock(LabTestCultureOptionRepository.class);
        private final LabTestRepository labTestRepository = mock(LabTestRepository.class);
        private final CultureOptionServiceImpl service =
                new CultureOptionServiceImpl(optionRepository, linkRepository, labTestRepository);

        private LabTest ecbu;

        @BeforeEach
        void setUp() {
            ecbu = new LabTest();
            ecbu.setId(UUID.randomUUID());
            ecbu.setName("ECBU");
            ecbu.setDiscipline(Discipline.BIOLOGY);
            ecbu.setBiologyKind(BiologyKind.CULTURE);
            when(labTestRepository.findByIdAndBranchId(ecbu.getId(), BRANCH)).thenReturn(Optional.of(ecbu));
            when(optionRepository.save(any())).then(returnsFirstArg());
            when(linkRepository.save(any())).then(returnsFirstArg());
        }

        private BiologyCultureOption option(String nom) {
            BiologyCultureOption o = new BiologyCultureOption();
            o.setId(UUID.randomUUID());
            o.setName(nom);
            return o;
        }

        private LabTestCultureOption lien(BiologyCultureOption o, int position) {
            LabTestCultureOption l = new LabTestCultureOption();
            l.setId(UUID.randomUUID());
            l.setLabTest(ecbu);
            l.setCultureOption(o);
            l.setPosition(position);
            return l;
        }

        @Test
        @DisplayName("création : choix rognés et dédoublonnés ; liste vide = saisie libre")
        void creation() {
            CultureOptionRequestDto dto = new CultureOptionRequestDto();
            dto.setName(" Aspect ");
            dto.setChoices(List.of("Clair", " clair ", "Trouble", " "));
            CultureOptionResponseDto cree = service.create(dto, BRANCH);
            assertThat(cree.name()).isEqualTo("Aspect");
            assertThat(cree.choices()).containsExactly("Clair", "Trouble");

            CultureOptionRequestDto libre = new CultureOptionRequestDto();
            libre.setName("Remarque");
            assertThat(service.create(libre, BRANCH).choices()).isEmpty();
        }

        @Test
        @DisplayName("nom déjà pris : 409")
        void doublon() {
            when(optionRepository.existsByNameIgnoreCaseAndBranchId("Aspect", BRANCH)).thenReturn(true);
            CultureOptionRequestDto dto = new CultureOptionRequestDto();
            dto.setName("Aspect");
            assertThatThrownBy(() -> service.create(dto, BRANCH)).isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("suppression : l'option et ses rattachements aux analyses")
        void suppression() {
            BiologyCultureOption aspect = option("Aspect");
            LabTestCultureOption l = lien(aspect, 0);
            when(optionRepository.findByIdAndBranchId(aspect.getId(), BRANCH)).thenReturn(Optional.of(aspect));
            when(linkRepository.findByCultureOption_Id(aspect.getId())).thenReturn(List.of(l));

            service.delete(aspect.getId(), BRANCH);

            verify(linkRepository).deleteAll(List.of(l));
            verify(optionRepository).delete(aspect);
        }

        @Test
        @DisplayName("rattachement : ajoute, réordonne et retire selon la liste")
        void rattachement() {
            BiologyCultureOption aspect = option("Aspect");
            BiologyCultureOption germe = option("Germe");
            BiologyCultureOption odeur = option("Odeur");
            LabTestCultureOption lienAspect = lien(aspect, 0);
            LabTestCultureOption lienOdeur = lien(odeur, 1);
            when(optionRepository.findByIdInAndBranchId(List.of(germe.getId(), aspect.getId()), BRANCH))
                    .thenReturn(List.of(aspect, germe));
            when(linkRepository.findByLabTest_IdOrderByPositionAsc(ecbu.getId()))
                    .thenReturn(List.of(lienAspect, lienOdeur));

            service.setForLabTest(ecbu.getId(), List.of(germe.getId(), aspect.getId()), BRANCH);

            verify(linkRepository).deleteAll(List.of(lienOdeur));
            ArgumentCaptor<LabTestCultureOption> enregistres = ArgumentCaptor.forClass(LabTestCultureOption.class);
            verify(linkRepository, times(2)).save(enregistres.capture());
            LabTestCultureOption nouveau = enregistres.getAllValues().get(0);
            assertThat(nouveau.getId()).isNull();
            assertThat(nouveau.getCultureOption()).isSameAs(germe);
            assertThat(nouveau.getPosition()).isZero();
            assertThat(nouveau.getBranchId()).isEqualTo(BRANCH);
            assertThat(enregistres.getAllValues().get(1)).isSameAs(lienAspect);
            assertThat(lienAspect.getPosition()).isEqualTo(1);
        }

        @Test
        @DisplayName("rattachement : doublon refusé, option inconnue introuvable")
        void rattachementInvalide() {
            UUID id = UUID.randomUUID();
            assertThatThrownBy(() -> service.setForLabTest(ecbu.getId(), List.of(id, id), BRANCH))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("deux fois");

            when(optionRepository.findByIdInAndBranchId(List.of(id), BRANCH)).thenReturn(List.of());
            assertThatThrownBy(() -> service.setForLabTest(ecbu.getId(), List.of(id), BRANCH))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(linkRepository, never()).save(any());
        }

        @Test
        @DisplayName("rattachement réservé aux analyses CULTURE")
        void reserveAuxCultures() {
            LabTest nfs = new LabTest();
            nfs.setId(UUID.randomUUID());
            nfs.setName("NFS");
            nfs.setDiscipline(Discipline.BIOLOGY);
            nfs.setBiologyKind(BiologyKind.PANEL);
            when(labTestRepository.findByIdAndBranchId(nfs.getId(), BRANCH)).thenReturn(Optional.of(nfs));

            assertThatThrownBy(() -> service.setForLabTest(nfs.getId(), List.of(), BRANCH))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("CULTURE");
            assertThatThrownBy(() -> service.findByLabTest(nfs.getId(), BRANCH))
                    .isInstanceOf(BusinessException.class);
        }
    }
}

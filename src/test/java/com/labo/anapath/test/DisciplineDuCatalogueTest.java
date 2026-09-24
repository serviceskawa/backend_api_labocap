package com.labo.anapath.test;

import com.labo.anapath.biology.BiologyKind;
import com.labo.anapath.common.Discipline;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.module.ModulesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Règles de discipline du catalogue : défaut PATHOLOGY, biologie fermée sans le
 * module, discipline et nature fixées à la création, catégorie de même discipline,
 * catégories de biologie par défaut.
 */
class DisciplineDuCatalogueTest {

    private static final UUID BRANCH = UUID.randomUUID();

    private final LabTestRepository labTestRepository = mock(LabTestRepository.class);
    private final CategoryTestRepository categoryTestRepository = mock(CategoryTestRepository.class);
    private final UnitMeasurementRepository unitMeasurementRepository = mock(UnitMeasurementRepository.class);
    private final TestCatalogueMapper mapper = mock(TestCatalogueMapper.class);
    private final ModulesProperties modules = new ModulesProperties();

    private static CategoryTest categorie(String nom, Discipline discipline) {
        CategoryTest c = new CategoryTest();
        c.setId(UUID.randomUUID());
        c.setName(nom);
        c.setDiscipline(discipline);
        return c;
    }

    @Nested
    @DisplayName("analyses")
    class Analyses {

        private final LabTestServiceImpl service = new LabTestServiceImpl(
                labTestRepository, categoryTestRepository, unitMeasurementRepository, mapper, modules);

        @BeforeEach
        void setUp() {
            when(mapper.toLabTestEntity(any())).thenAnswer(inv -> {
                LabTestRequestDto dto = inv.getArgument(0);
                LabTest t = new LabTest();
                t.setName(dto.getName());
                t.setSpecimenType(dto.getSpecimenType());
                return t;
            });
            when(labTestRepository.save(any())).then(returnsFirstArg());
        }

        private LabTestRequestDto requete(Discipline discipline) {
            LabTestRequestDto dto = new LabTestRequestDto();
            dto.setName("NFS");
            dto.setPrice(BigDecimal.valueOf(5000));
            dto.setDiscipline(discipline);
            return dto;
        }

        private LabTest creee() {
            ArgumentCaptor<LabTest> t = ArgumentCaptor.forClass(LabTest.class);
            verify(labTestRepository).save(t.capture());
            return t.getValue();
        }

        @Test
        @DisplayName("sans discipline : PATHOLOGY, sans nature biologique")
        void defautPathologie() {
            service.create(requete(null), BRANCH);

            assertThat(creee().getDiscipline()).isEqualTo(Discipline.PATHOLOGY);
            assertThat(creee().getBiologyKind()).isNull();
        }

        @Test
        @DisplayName("BIOLOGY refusée tant que le module est désactivé")
        void biologieSansModule() {
            modules.setBiology(false);

            assertThatThrownBy(() -> service.create(requete(Discipline.BIOLOGY), BRANCH))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("module Biologie");
            verify(labTestRepository, never()).save(any());
        }

        @Test
        @DisplayName("BIOLOGY avec le module : nature PANEL par défaut, type d'échantillon gardé")
        void biologieAvecModule() {
            modules.setBiology(true);
            LabTestRequestDto dto = requete(Discipline.BIOLOGY);
            dto.setSpecimenType("Sang total EDTA");

            service.create(dto, BRANCH);

            assertThat(creee().getDiscipline()).isEqualTo(Discipline.BIOLOGY);
            assertThat(creee().getBiologyKind()).isEqualTo(BiologyKind.PANEL);
            assertThat(creee().getSpecimenType()).isEqualTo("Sang total EDTA");
        }

        @Test
        @DisplayName("BIOLOGY CULTURE demandée : retenue")
        void culture() {
            modules.setBiology(true);
            LabTestRequestDto dto = requete(Discipline.BIOLOGY);
            dto.setBiologyKind(BiologyKind.CULTURE);

            service.create(dto, BRANCH);

            assertThat(creee().getBiologyKind()).isEqualTo(BiologyKind.CULTURE);
        }

        @Test
        @DisplayName("PATHOLOGY avec une nature ou un type d'échantillon : refusée")
        void champsDeBiologieEnPathologie() {
            LabTestRequestDto dto = requete(Discipline.PATHOLOGY);
            dto.setBiologyKind(BiologyKind.PANEL);
            assertThatThrownBy(() -> service.create(dto, BRANCH)).isInstanceOf(BusinessException.class);

            LabTestRequestDto dto2 = requete(null);
            dto2.setSpecimenType("Urines");
            assertThatThrownBy(() -> service.create(dto2, BRANCH)).isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("catégorie d'une autre discipline : refusée à la création comme à la modification")
        void categorieDAutreDiscipline() {
            modules.setBiology(true);
            CategoryTest cytologie = categorie("Cytologie", Discipline.PATHOLOGY);
            when(categoryTestRepository.findById(cytologie.getId())).thenReturn(Optional.of(cytologie));
            LabTestRequestDto dto = requete(Discipline.BIOLOGY);
            dto.setCategoryTestId(cytologie.getId());

            assertThatThrownBy(() -> service.create(dto, BRANCH))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("même discipline");

            LabTest nfs = new LabTest();
            nfs.setId(UUID.randomUUID());
            nfs.setDiscipline(Discipline.BIOLOGY);
            nfs.setBiologyKind(BiologyKind.PANEL);
            when(labTestRepository.findById(nfs.getId())).thenReturn(Optional.of(nfs));
            LabTestRequestDto maj = requete(null);
            maj.setCategoryTestId(cytologie.getId());
            assertThatThrownBy(() -> service.update(nfs.getId(), maj))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("même discipline");
        }

        @Test
        @DisplayName("catégorie de même discipline : acceptée")
        void categorieDeMemeDiscipline() {
            modules.setBiology(true);
            CategoryTest hematologie = categorie("Hématologie", Discipline.BIOLOGY);
            when(categoryTestRepository.findById(hematologie.getId())).thenReturn(Optional.of(hematologie));
            LabTestRequestDto dto = requete(Discipline.BIOLOGY);
            dto.setCategoryTestId(hematologie.getId());

            service.create(dto, BRANCH);

            assertThat(creee().getCategoryTest()).isSameAs(hematologie);
        }

        @Test
        @DisplayName("modification : discipline et nature immuables, les renvoyer à l'identique est permis")
        void immuables() {
            LabTest nfs = new LabTest();
            nfs.setId(UUID.randomUUID());
            nfs.setDiscipline(Discipline.BIOLOGY);
            nfs.setBiologyKind(BiologyKind.PANEL);
            when(labTestRepository.findById(nfs.getId())).thenReturn(Optional.of(nfs));

            assertThatThrownBy(() -> service.update(nfs.getId(), requete(Discipline.PATHOLOGY)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("discipline");
            LabTestRequestDto nature = requete(Discipline.BIOLOGY);
            nature.setBiologyKind(BiologyKind.CULTURE);
            assertThatThrownBy(() -> service.update(nfs.getId(), nature))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("nature");

            LabTestRequestDto identique = requete(Discipline.BIOLOGY);
            identique.setBiologyKind(BiologyKind.PANEL);
            service.update(nfs.getId(), identique);
            verify(mapper).updateLabTestFromDto(identique, nfs);
        }

        @Test
        @DisplayName("listes : discipline absente = PATHOLOGY")
        void listes() {
            service.findAll(BRANCH, null);
            verify(labTestRepository).findAllByBranchIdAndDisciplineOrderByCreatedAtDesc(BRANCH, Discipline.PATHOLOGY);

            service.search("nfs", BRANCH, Discipline.BIOLOGY);
            verify(labTestRepository).findByNameContainingIgnoreCaseAndBranchId("nfs", BRANCH, "BIOLOGY");
        }
    }

    @Nested
    @DisplayName("catégories")
    class Categories {

        private final CategoryTestServiceImpl service =
                new CategoryTestServiceImpl(categoryTestRepository, labTestRepository, mapper, modules);

        @BeforeEach
        void setUp() {
            when(mapper.toCategoryTestEntity(any())).thenAnswer(inv -> {
                CategoryTest c = new CategoryTest();
                c.setName(((CategoryTestRequestDto) inv.getArgument(0)).getName());
                return c;
            });
            when(categoryTestRepository.save(any())).then(returnsFirstArg());
        }

        private CategoryTestRequestDto requete(String nom, Discipline discipline) {
            CategoryTestRequestDto dto = new CategoryTestRequestDto();
            dto.setName(nom);
            dto.setDiscipline(discipline);
            return dto;
        }

        @Test
        @DisplayName("création : PATHOLOGY par défaut ; BIOLOGY refusée sans le module")
        void creation() {
            service.create(requete("Cytologie", null), BRANCH);
            ArgumentCaptor<CategoryTest> c = ArgumentCaptor.forClass(CategoryTest.class);
            verify(categoryTestRepository).save(c.capture());
            assertThat(c.getValue().getDiscipline()).isEqualTo(Discipline.PATHOLOGY);

            modules.setBiology(false);
            assertThatThrownBy(() -> service.create(requete("Hématologie", Discipline.BIOLOGY), BRANCH))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("module Biologie");
        }

        @Test
        @DisplayName("le nom n'est unique qu'au sein d'une discipline")
        void nomParDiscipline() {
            modules.setBiology(true);
            when(categoryTestRepository.existsByNameIgnoreCaseAndBranchIdAndDiscipline("Hématologie", BRANCH,
                    Discipline.PATHOLOGY)).thenReturn(true);

            service.create(requete("Hématologie", Discipline.BIOLOGY), BRANCH);

            verify(categoryTestRepository).existsByNameIgnoreCaseAndBranchIdAndDiscipline("Hématologie", BRANCH,
                    Discipline.BIOLOGY);
            verify(categoryTestRepository).save(any());
        }

        @Test
        @DisplayName("modification : la discipline ne change pas")
        void immuable() {
            CategoryTest cytologie = categorie("Cytologie", Discipline.PATHOLOGY);
            when(categoryTestRepository.findById(cytologie.getId())).thenReturn(Optional.of(cytologie));

            assertThatThrownBy(() -> service.update(cytologie.getId(), requete("Cytologie", Discipline.BIOLOGY)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("discipline");
        }

        @Test
        @DisplayName("catégories par défaut : refusées sans le module")
        void defautsSansModule() {
            modules.setBiology(false);

            assertThatThrownBy(() -> service.createBiologyDefaults(BRANCH))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("module Biologie");
            verify(categoryTestRepository, never()).save(any());
        }

        @Test
        @DisplayName("catégories par défaut : seules les absentes sont créées (idempotent)")
        void defautsIdempotents() {
            modules.setBiology(true);
            when(categoryTestRepository.existsByNameIgnoreCaseAndBranchIdAndDiscipline(eq("Biochimie"), eq(BRANCH),
                    eq(Discipline.BIOLOGY))).thenReturn(true);

            service.createBiologyDefaults(BRANCH);

            ArgumentCaptor<CategoryTest> creees = ArgumentCaptor.forClass(CategoryTest.class);
            verify(categoryTestRepository, times(4)).save(creees.capture());
            assertThat(creees.getAllValues()).extracting(CategoryTest::getName)
                    .containsExactly("Hématologie", "Immunologie/Sérologie", "Bactériologie", "Parasitologie");
            assertThat(creees.getAllValues()).allSatisfy(c -> {
                assertThat(c.getDiscipline()).isEqualTo(Discipline.BIOLOGY);
                assertThat(c.getBranchId()).isEqualTo(BRANCH);
                assertThat(c.getCode()).isNull();
            });
            verify(categoryTestRepository).findAllByBranchIdAndDisciplineOrderByName(BRANCH, Discipline.BIOLOGY);
        }

        @Test
        @DisplayName("catégories par défaut déjà toutes présentes : rien n'est créé")
        void defautsDejaPresents() {
            modules.setBiology(true);
            when(categoryTestRepository.existsByNameIgnoreCaseAndBranchIdAndDiscipline(any(), eq(BRANCH),
                    eq(Discipline.BIOLOGY))).thenReturn(true);
            when(categoryTestRepository.findAllByBranchIdAndDisciplineOrderByName(BRANCH, Discipline.BIOLOGY))
                    .thenReturn(List.of(categorie("Hématologie", Discipline.BIOLOGY)));

            assertThat(service.createBiologyDefaults(BRANCH)).hasSize(1);
            verify(categoryTestRepository, never()).save(any());
        }
    }
}

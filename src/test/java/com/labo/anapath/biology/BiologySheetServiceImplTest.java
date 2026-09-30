package com.labo.anapath.biology;

import com.labo.anapath.common.Discipline;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.test.LabTest;
import com.labo.anapath.test.LabTestRepository;
import com.labo.anapath.test.UnitMeasurement;
import com.labo.anapath.test.UnitMeasurementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.labo.anapath.biology.BiologySheetValidatorTest.parametre;
import static com.labo.anapath.biology.BiologySheetValidatorTest.plage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Enregistrement et lecture d'une fiche de paramètres.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BiologySheetServiceImplTest {

    @Mock private LabTestRepository labTestRepository;
    @Mock private BiologySectionRepository sectionRepository;
    @Mock private BiologyParameterRepository parameterRepository;
    @Mock private BiologyReferenceRangeRepository rangeRepository;
    @Mock private UnitMeasurementRepository unitMeasurementRepository;

    @InjectMocks
    private BiologySheetServiceImpl service;

    private static final UUID BRANCH = UUID.randomUUID();
    private LabTest nfs;

    @BeforeEach
    void setUp() {
        nfs = analyse("NFS", Discipline.BIOLOGY, BiologyKind.PANEL);
        when(labTestRepository.findByIdAndBranchId(nfs.getId(), BRANCH)).thenReturn(Optional.of(nfs));
        when(sectionRepository.save(any())).then(returnsFirstArg());
        when(parameterRepository.save(any())).then(returnsFirstArg());
        when(rangeRepository.save(any())).then(returnsFirstArg());
    }

    private static LabTest analyse(String nom, Discipline discipline, BiologyKind nature) {
        LabTest t = new LabTest();
        t.setId(UUID.randomUUID());
        t.setName(nom);
        t.setDiscipline(discipline);
        t.setBiologyKind(nature);
        t.setBranchId(BRANCH);
        return t;
    }

    private BiologyParameter parametreExistant(String nom, String code, BiologySection section) {
        BiologyParameter p = new BiologyParameter();
        p.setId(UUID.randomUUID());
        p.setLabTest(nfs);
        p.setName(nom);
        p.setCode(code);
        p.setSection(section);
        p.setResultType(ResultType.NUMERIC);
        return p;
    }

    private static BiologyReferenceRange plageExistante(BiologyParameter p) {
        BiologyReferenceRange r = new BiologyReferenceRange();
        r.setId(UUID.randomUUID());
        r.setParameter(p);
        r.setLow(BigDecimal.ONE);
        return r;
    }

    // ------------------------------------------------------------------ gardes

    @Test
    @DisplayName("une analyse d'anatomie pathologique n'a pas de fiche")
    void refusePathologie() {
        LabTest biopsie = analyse("Biopsie", Discipline.PATHOLOGY, null);
        when(labTestRepository.findByIdAndBranchId(biopsie.getId(), BRANCH)).thenReturn(Optional.of(biopsie));

        assertThatThrownBy(() -> service.saveSheet(biopsie.getId(), new BiologySheetRequestDto(), BRANCH))
                .isInstanceOf(BusinessException.class).hasMessageContaining("n'est pas une analyse de biologie");
        assertThatThrownBy(() -> service.getSheet(biopsie.getId(), BRANCH))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("une culture n'a pas de fiche de paramètres")
    void refuseCulture() {
        LabTest ecbu = analyse("ECBU", Discipline.BIOLOGY, BiologyKind.CULTURE);
        when(labTestRepository.findByIdAndBranchId(ecbu.getId(), BRANCH)).thenReturn(Optional.of(ecbu));

        assertThatThrownBy(() -> service.saveSheet(ecbu.getId(), new BiologySheetRequestDto(), BRANCH))
                .isInstanceOf(BusinessException.class).hasMessageContaining("PANEL");
        verify(parameterRepository, never()).save(any());
    }

    @Test
    @DisplayName("une analyse d'une autre succursale est introuvable")
    void autreSuccursale() {
        UUID ailleurs = UUID.randomUUID();
        when(labTestRepository.findByIdAndBranchId(ailleurs, BRANCH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveSheet(ailleurs, new BiologySheetRequestDto(), BRANCH))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("une fiche incohérente n'écrit rien")
    void ficheIncoherente() {
        BiologySheetRequestDto.ParameterRequest p = parametre("Hb", ResultType.NUMERIC);
        p.setRanges(List.of(plage("5", "1")));

        assertThatThrownBy(() -> service.saveSheet(nfs.getId(), BiologySheetValidatorTest.fiche(p), BRANCH))
                .isInstanceOf(BusinessException.class);
        verify(parameterRepository, never()).save(any());
        verify(rangeRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ création

    @Test
    @DisplayName("création : sections, paramètres et plages, positions selon l'ordre de la requête")
    void creation() {
        UnitMeasurement gdl = new UnitMeasurement();
        gdl.setId(UUID.randomUUID());
        gdl.setName("gramme par décilitre");
        when(unitMeasurementRepository.findById(gdl.getId())).thenReturn(Optional.of(gdl));

        BiologySheetRequestDto.ParameterRequest hb = parametre("Hémoglobine", ResultType.NUMERIC);
        hb.setUnitMeasurementId(gdl.getId());
        BiologySheetRequestDto.RangeRequest homme = plage("13", "17");
        homme.setSex("M");
        hb.setRanges(List.of(homme, plage("12", "16")));
        BiologySheetRequestDto.ParameterRequest ht = parametre("Hématocrite", ResultType.NUMERIC);
        ht.setUnitMeasurementId(gdl.getId());
        BiologySheetRequestDto.SectionRequest hemogramme = new BiologySheetRequestDto.SectionRequest();
        hemogramme.setTitle(" Hémogramme ");
        hemogramme.setParameters(List.of(hb, ht));
        BiologySheetRequestDto.ParameterRequest commentaire = parametre("Commentaire", ResultType.TEXT);
        commentaire.setPrintable(false);
        BiologySheetRequestDto fiche = new BiologySheetRequestDto();
        fiche.setSections(List.of(hemogramme));
        fiche.setParameters(List.of(commentaire));

        service.saveSheet(nfs.getId(), fiche, BRANCH);

        ArgumentCaptor<BiologySection> section = ArgumentCaptor.forClass(BiologySection.class);
        verify(sectionRepository).save(section.capture());
        assertThat(section.getValue().getTitle()).isEqualTo("Hémogramme");
        assertThat(section.getValue().getLabTest()).isSameAs(nfs);
        assertThat(section.getValue().getBranchId()).isEqualTo(BRANCH);

        ArgumentCaptor<BiologyParameter> parametres = ArgumentCaptor.forClass(BiologyParameter.class);
        verify(parameterRepository, atLeastOnce()).save(parametres.capture());
        assertThat(parametres.getAllValues()).extracting(BiologyParameter::getName, BiologyParameter::getPosition)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Hémoglobine", 0),
                        org.assertj.core.groups.Tuple.tuple("Hématocrite", 1),
                        org.assertj.core.groups.Tuple.tuple("Commentaire", 0));
        assertThat(parametres.getAllValues().get(0).getSection()).isSameAs(section.getValue());
        assertThat(parametres.getAllValues().get(2).getSection()).isNull();
        assertThat(parametres.getAllValues().get(2).isPrintable()).isFalse();
        assertThat(parametres.getAllValues().get(0).getUnitMeasurement()).isSameAs(gdl);
        // Une seule lecture de l'unité pour deux paramètres qui la partagent.
        verify(unitMeasurementRepository).findById(gdl.getId());

        ArgumentCaptor<BiologyReferenceRange> plages = ArgumentCaptor.forClass(BiologyReferenceRange.class);
        verify(rangeRepository, org.mockito.Mockito.times(2)).save(plages.capture());
        assertThat(plages.getAllValues()).extracting(BiologyReferenceRange::getSex, BiologyReferenceRange::getPosition)
                .containsExactly(org.assertj.core.groups.Tuple.tuple('M', 0),
                        org.assertj.core.groups.Tuple.tuple(null, 1));
        assertThat(plages.getAllValues().get(0).getParameter()).isSameAs(parametres.getAllValues().get(0));
    }

    @Test
    @DisplayName("unité inconnue : 404")
    void uniteInconnue() {
        UUID inconnue = UUID.randomUUID();
        when(unitMeasurementRepository.findById(inconnue)).thenReturn(Optional.empty());
        BiologySheetRequestDto.ParameterRequest hb = parametre("Hb", ResultType.NUMERIC);
        hb.setUnitMeasurementId(inconnue);

        assertThatThrownBy(() -> service.saveSheet(nfs.getId(), BiologySheetValidatorTest.fiche(hb), BRANCH))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ------------------------------------------------------------------ mise à jour

    @Test
    @DisplayName("mise à jour : les éléments absents sont supprimés, purge avant les créations")
    void suppressionDesAbsents() {
        BiologySection ancienne = new BiologySection();
        ancienne.setId(UUID.randomUUID());
        ancienne.setLabTest(nfs);
        BiologyParameter garde = parametreExistant("Hémoglobine", "HB", ancienne);
        BiologyParameter retire = parametreExistant("VGM", "VGM", ancienne);
        BiologyReferenceRange plageGardee = plageExistante(garde);
        BiologyReferenceRange plageRetiree = plageExistante(garde);
        BiologyReferenceRange plageDuRetire = plageExistante(retire);
        when(sectionRepository.findByLabTest_IdOrderByPositionAsc(nfs.getId())).thenReturn(List.of(ancienne));
        when(parameterRepository.findByLabTest_IdOrderByPositionAsc(nfs.getId())).thenReturn(List.of(garde, retire));
        when(rangeRepository.findByParameter_IdInOrderByPositionAsc(anyCollection()))
                .thenReturn(List.of(plageGardee, plageRetiree, plageDuRetire));

        BiologySheetRequestDto.ParameterRequest hb = parametre("Hémoglobine", ResultType.NUMERIC);
        hb.setId(garde.getId());
        hb.setCode("HB");
        BiologySheetRequestDto.RangeRequest r = plage("12", "16");
        r.setId(plageGardee.getId());
        hb.setRanges(List.of(r));
        // Un nouveau paramètre reprend le code « VGM » du paramètre supprimé.
        BiologySheetRequestDto.ParameterRequest vgm = parametre("VGM (nouveau)", ResultType.NUMERIC);
        vgm.setCode("VGM");

        service.saveSheet(nfs.getId(), BiologySheetValidatorTest.fiche(hb, vgm), BRANCH);

        verify(rangeRepository).deleteAll(List.of(plageRetiree, plageDuRetire));
        verify(parameterRepository).deleteAll(List.of(retire));
        verify(sectionRepository).deleteAll(List.of(ancienne));
        assertThat(garde.getSection()).isNull();
        assertThat(plageGardee.getHigh()).isEqualByComparingTo("16");

        InOrder ordre = inOrder(parameterRepository);
        ordre.verify(parameterRepository).deleteAll(any());
        ordre.verify(parameterRepository).flush();
        ordre.verify(parameterRepository).save(garde);
        ordre.verify(parameterRepository).flush();
        ordre.verify(parameterRepository).save(org.mockito.ArgumentMatchers.argThat(p -> p.getId() == null));
    }

    @Test
    @DisplayName("échange de codes entre deux paramètres : les codes sont libérés et purgés d'abord")
    void echangeDeCodes() {
        BiologyParameter a = parametreExistant("A", "X", null);
        BiologyParameter b = parametreExistant("B", "Y", null);
        when(parameterRepository.findByLabTest_IdOrderByPositionAsc(nfs.getId())).thenReturn(List.of(a, b));
        List<String> codesAuFlush = new ArrayList<>();
        org.mockito.Mockito.doAnswer(inv -> {
            codesAuFlush.add(a.getCode() + "/" + b.getCode());
            return null;
        }).when(parameterRepository).flush();

        BiologySheetRequestDto.ParameterRequest pa = parametre("A", ResultType.NUMERIC);
        pa.setId(a.getId());
        pa.setCode("Y");
        BiologySheetRequestDto.ParameterRequest pb = parametre("B", ResultType.NUMERIC);
        pb.setId(b.getId());
        pb.setCode("X");

        service.saveSheet(nfs.getId(), BiologySheetValidatorTest.fiche(pa, pb), BRANCH);

        assertThat(codesAuFlush).first().isEqualTo("null/null");
        assertThat(a.getCode()).isEqualTo("Y");
        assertThat(b.getCode()).isEqualTo("X");
    }

    @Test
    @DisplayName("un identifiant d'une autre analyse est refusé")
    void identifiantEtranger() {
        BiologySheetRequestDto.ParameterRequest p = parametre("Hb", ResultType.NUMERIC);
        p.setId(UUID.randomUUID());

        assertThatThrownBy(() -> service.saveSheet(nfs.getId(), BiologySheetValidatorTest.fiche(p), BRANCH))
                .isInstanceOf(BusinessException.class).hasMessageContaining("n'appartient pas à cette analyse");
        verify(parameterRepository, never()).deleteAll(any());
    }

    @Test
    @DisplayName("une plage ne change pas de paramètre")
    void plageQuiDemenage() {
        BiologyParameter a = parametreExistant("A", null, null);
        BiologyParameter b = parametreExistant("B", null, null);
        BiologyReferenceRange plageDeA = plageExistante(a);
        when(parameterRepository.findByLabTest_IdOrderByPositionAsc(nfs.getId())).thenReturn(List.of(a, b));
        when(rangeRepository.findByParameter_IdInOrderByPositionAsc(anyCollection())).thenReturn(List.of(plageDeA));

        BiologySheetRequestDto.ParameterRequest pa = parametre("A", ResultType.NUMERIC);
        pa.setId(a.getId());
        BiologySheetRequestDto.ParameterRequest pb = parametre("B", ResultType.NUMERIC);
        pb.setId(b.getId());
        BiologySheetRequestDto.RangeRequest r = plage("1", "2");
        r.setId(plageDeA.getId());
        pb.setRanges(List.of(r));

        assertThatThrownBy(() -> service.saveSheet(nfs.getId(), BiologySheetValidatorTest.fiche(pa, pb), BRANCH))
                .isInstanceOf(BusinessException.class).hasMessageContaining("changer de paramètre");
    }

    // ------------------------------------------------------------------ lecture

    @Test
    @DisplayName("lecture : paramètres rangés sous leur section, plages sous leur paramètre")
    void lecture() {
        BiologySection hemogramme = new BiologySection();
        hemogramme.setId(UUID.randomUUID());
        hemogramme.setTitle("Hémogramme");
        BiologySection supprimee = new BiologySection();
        supprimee.setId(UUID.randomUUID());
        BiologyParameter hb = parametreExistant("Hémoglobine", "HB", hemogramme);
        BiologyParameter orphelin = parametreExistant("Orphelin", null, supprimee);
        BiologyParameter libre = parametreExistant("Commentaire", null, null);
        BiologyReferenceRange r = plageExistante(hb);
        r.setSex('F');
        when(sectionRepository.findByLabTest_IdOrderByPositionAsc(nfs.getId())).thenReturn(List.of(hemogramme));
        when(parameterRepository.findByLabTest_IdOrderByPositionAsc(nfs.getId())).thenReturn(List.of(hb, orphelin, libre));
        when(rangeRepository.findByParameter_IdInOrderByPositionAsc(anyCollection())).thenReturn(List.of(r));

        BiologySheetResponseDto fiche = service.getSheet(nfs.getId(), BRANCH);

        assertThat(fiche.labTestName()).isEqualTo("NFS");
        assertThat(fiche.sections()).hasSize(1);
        assertThat(fiche.sections().get(0).parameters()).extracting(BiologySheetResponseDto.Parameter::name)
                .containsExactly("Hémoglobine");
        assertThat(fiche.sections().get(0).parameters().get(0).ranges()).singleElement()
                .extracting(BiologySheetResponseDto.Range::sex).isEqualTo("F");
        // Un paramètre dont la section n'existe plus retombe hors section.
        assertThat(fiche.parameters()).extracting(BiologySheetResponseDto.Parameter::name)
                .containsExactly("Orphelin", "Commentaire");
    }

    @Test
    @DisplayName("lecture d'une analyse sans paramètre : fiche vide, sans requête sur les plages")
    void lectureVide() {
        BiologySheetResponseDto fiche = service.getSheet(nfs.getId(), BRANCH);

        assertThat(fiche.sections()).isEmpty();
        assertThat(fiche.parameters()).isEmpty();
        verify(rangeRepository, never()).findByParameter_IdInOrderByPositionAsc(anyCollection());
    }
}

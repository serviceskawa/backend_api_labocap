package com.labo.anapath.biology.results;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labo.anapath.biology.Antibiotic;
import com.labo.anapath.biology.AntibioticRepository;
import com.labo.anapath.biology.BiologyCultureOption;
import com.labo.anapath.biology.BiologyCultureOptionRepository;
import com.labo.anapath.biology.BiologyKind;
import com.labo.anapath.biology.BiologyParameter;
import com.labo.anapath.biology.BiologyParameterRepository;
import com.labo.anapath.biology.BiologyReferenceRange;
import com.labo.anapath.biology.BiologyReferenceRangeRepository;
import com.labo.anapath.biology.BiologySectionRepository;
import com.labo.anapath.biology.LabTestCultureOption;
import com.labo.anapath.biology.LabTestCultureOptionRepository;
import com.labo.anapath.biology.ResultType;
import com.labo.anapath.common.Discipline;
import com.labo.anapath.common.audit.AuditableEntity;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.patient.Patient;
import com.labo.anapath.report.LogReport;
import com.labo.anapath.report.LogReportRepository;
import com.labo.anapath.report.Report;
import com.labo.anapath.report.ReportRepository;
import com.labo.anapath.report.ReportStatus;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import com.labo.anapath.test.LabTest;
import com.labo.anapath.test.LabTestRepository;
import com.labo.anapath.test.UnitMeasurement;
import com.labo.anapath.testorder.DetailTestOrder;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Saisie des résultats : indicateurs et copies à la saisie, verrous, validation
 * technique et transitions du compte-rendu (ONE_STEP / TWO_STEP), cultures.
 *
 * <p>Les tables de résultats sont simulées en mémoire (enregistrer, supprimer, relire) ;
 * l'avancement du compte-rendu et les réglages sont les vrais composants.</p>
 */
class BiologyResultServiceImplTest {

    private static final UUID BRANCH = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    /** Table simulée : l'enregistrement donne un identifiant, la suppression retire la ligne. */
    static final class Table<T extends AuditableEntity> {
        final List<T> lignes = new ArrayList<>();

        T save(T t) {
            if (t.getId() == null) t.setId(UUID.randomUUID());
            if (!lignes.contains(t)) lignes.add(t);
            return t;
        }

        List<T> ou(Function<T, UUID> cle, Collection<UUID> ids) {
            return lignes.stream().filter(t -> ids.contains(cle.apply(t))).toList();
        }
    }

    private final TestOrderRepository testOrderRepository = mock(TestOrderRepository.class);
    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final LabTestRepository labTestRepository = mock(LabTestRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BiologySectionRepository sectionRepository = mock(BiologySectionRepository.class);
    private final BiologyParameterRepository parameterRepository = mock(BiologyParameterRepository.class);
    private final BiologyReferenceRangeRepository rangeRepository = mock(BiologyReferenceRangeRepository.class);
    private final LabTestCultureOptionRepository ltcoRepository = mock(LabTestCultureOptionRepository.class);
    private final BiologyCultureOptionRepository cultureOptionRepository = mock(BiologyCultureOptionRepository.class);
    private final AntibioticRepository antibioticRepository = mock(AntibioticRepository.class);
    private final BiologyAnalysisResultRepository analysisRepo = mock(BiologyAnalysisResultRepository.class);
    private final BiologyParameterResultRepository paramRepo = mock(BiologyParameterResultRepository.class);
    private final BiologyCultureResultRepository cultureRepo = mock(BiologyCultureResultRepository.class);
    private final BiologyIsolateRepository isolateRepo = mock(BiologyIsolateRepository.class);
    private final BiologyAntibiogramResultRepository abgRepo = mock(BiologyAntibiogramResultRepository.class);
    private final LogReportRepository logRepo = mock(LogReportRepository.class);
    private final SettingAppRepository settingRepo = mock(SettingAppRepository.class);

    private final Table<BiologyAnalysisResult> analyses = new Table<>();
    private final Table<BiologyParameterResult> valeurs = new Table<>();
    private final Table<BiologyCultureResult> cultures = new Table<>();
    private final Table<BiologyIsolate> germes = new Table<>();
    private final Table<BiologyAntibiogramResult> antibiogrammes = new Table<>();

    private BiologyResultServiceImpl service;

    private TestOrder bon;
    private Report report;
    private LabTest nfs;
    private LabTest uroculture;
    private BiologyParameter hb;
    private BiologyParameter glu;
    private BiologyParameter aspect;
    private BiologyParameter remarque;
    private BiologyCultureOption optionAspect;
    private Antibiotic amoxicilline;
    private Antibiotic ciprofloxacine;

    @BeforeEach
    void setUp() {
        ReglagesDeBiologie reglages = new ReglagesDeBiologie(settingRepo, new ObjectMapper());
        AvancementDuCompteRendu avancement = new AvancementDuCompteRendu(analysisRepo, reportRepository, logRepo,
                userRepository, reglages);
        service = new BiologyResultServiceImpl(testOrderRepository, reportRepository, labTestRepository,
                userRepository, sectionRepository, parameterRepository, rangeRepository, ltcoRepository,
                cultureOptionRepository, antibioticRepository, analysisRepo, paramRepo, cultureRepo, isolateRepo,
                abgRepo, reglages, avancement);
        when(settingRepo.findByKeyAndBranchId(any(), any())).thenReturn(Optional.empty());
        User technicien = new User();
        technicien.setId(USER);
        technicien.setLastname("DOSSOU");
        technicien.setFirstname("Rita");
        when(userRepository.findById(USER)).thenReturn(Optional.of(technicien));
        when(userRepository.findAllById(any())).thenReturn(List.of(technicien));

        // --- Tables simulées ---------------------------------------------------------
        when(analysisRepo.save(any())).thenAnswer(i -> analyses.save(i.getArgument(0)));
        when(analysisRepo.findByTestOrderId(any())).thenAnswer(i -> analyses.lignes.stream()
                .filter(a -> a.getTestOrderId().equals(i.getArgument(0))).toList());
        when(analysisRepo.findByTestOrderIdAndLabTestId(any(), any())).thenAnswer(i -> analyses.lignes.stream()
                .filter(a -> a.getTestOrderId().equals(i.getArgument(0)) && a.getLabTestId().equals(i.getArgument(1)))
                .findFirst());
        when(paramRepo.save(any())).thenAnswer(i -> valeurs.save(i.getArgument(0)));
        doAnswer(i -> valeurs.lignes.remove(i.<BiologyParameterResult>getArgument(0))).when(paramRepo).delete(any());
        when(paramRepo.findByAnalysisResult_Id(any())).thenAnswer(i ->
                valeurs.ou(v -> v.getAnalysisResult().getId(), List.of(i.<UUID>getArgument(0))));
        when(paramRepo.findByAnalysisResult_IdIn(anyCollection())).thenAnswer(i ->
                valeurs.ou(v -> v.getAnalysisResult().getId(), i.getArgument(0)));
        when(cultureRepo.save(any())).thenAnswer(i -> cultures.save(i.getArgument(0)));
        doAnswer(i -> cultures.lignes.remove(i.<BiologyCultureResult>getArgument(0))).when(cultureRepo).delete(any());
        when(cultureRepo.findByAnalysisResult_Id(any())).thenAnswer(i ->
                cultures.ou(v -> v.getAnalysisResult().getId(), List.of(i.<UUID>getArgument(0))));
        when(cultureRepo.findByAnalysisResult_IdIn(anyCollection())).thenAnswer(i ->
                cultures.ou(v -> v.getAnalysisResult().getId(), i.getArgument(0)));
        when(isolateRepo.save(any())).thenAnswer(i -> germes.save(i.getArgument(0)));
        doAnswer(i -> germes.lignes.remove(i.<BiologyIsolate>getArgument(0))).when(isolateRepo).delete(any());
        when(isolateRepo.findByAnalysisResult_IdOrderByPositionAsc(any())).thenAnswer(i ->
                germes.ou(g -> g.getAnalysisResult().getId(), List.of(i.<UUID>getArgument(0))));
        when(isolateRepo.findByAnalysisResult_IdInOrderByPositionAsc(anyCollection())).thenAnswer(i ->
                germes.ou(g -> g.getAnalysisResult().getId(), i.getArgument(0)));
        when(abgRepo.save(any())).thenAnswer(i -> antibiogrammes.save(i.getArgument(0)));
        doAnswer(i -> antibiogrammes.lignes.remove(i.<BiologyAntibiogramResult>getArgument(0))).when(abgRepo).delete(any());
        when(abgRepo.findByIsolate_IdIn(anyCollection())).thenAnswer(i ->
                antibiogrammes.ou(a -> a.getIsolate().getId(), i.getArgument(0)));

        // --- Catalogue ---------------------------------------------------------------------
        nfs = analyse("NFS", BiologyKind.PANEL);
        uroculture = analyse("Uroculture", BiologyKind.CULTURE);
        when(labTestRepository.findAllById(any())).thenReturn(List.of(nfs, uroculture));

        UnitMeasurement gdl = new UnitMeasurement();
        gdl.setAbbreviation("g/dL");
        hb = parametre("Hémoglobine", ResultType.NUMERIC, (short) 1, 0);
        hb.setUnitMeasurement(gdl);
        glu = parametre("Glycémie", ResultType.NUMERIC, (short) 2, 1);
        glu.setReferenceText("Selon contexte");
        aspect = parametre("Aspect du sérum", ResultType.CHOICE, null, 2);
        aspect.setChoices(List.of("Clair", "Trouble"));
        remarque = parametre("Remarque", ResultType.TEXT, null, 3);
        remarque.setFlaggable(false);
        when(parameterRepository.findByLabTest_IdOrderByPositionAsc(nfs.getId()))
                .thenReturn(List.of(hb, glu, aspect, remarque));
        // Hémoglobine : homme [13 ; 17] critique [7 ; 20], par défaut [12 ; 16].
        BiologyReferenceRange homme = plage(hb, 'M', "13", "17", "7", "20");
        BiologyReferenceRange defaut = plage(hb, null, "12", "16", null, null);
        when(rangeRepository.findByParameter_IdInOrderByPositionAsc(anyCollection())).thenReturn(List.of(homme, defaut));

        optionAspect = new BiologyCultureOption();
        optionAspect.setId(UUID.randomUUID());
        optionAspect.setName("Aspect");
        optionAspect.setChoices(List.of("Clair", "Trouble"));
        LabTestCultureOption lien = new LabTestCultureOption();
        lien.setLabTest(uroculture);
        lien.setCultureOption(optionAspect);
        when(ltcoRepository.findByLabTest_IdOrderByPositionAsc(uroculture.getId())).thenReturn(List.of(lien));
        amoxicilline = antibiotique("Amoxicilline", 0);
        ciprofloxacine = antibiotique("Ciprofloxacine", 1);
        when(antibioticRepository.findAllById(any())).thenReturn(List.of(amoxicilline, ciprofloxacine));
        when(antibioticRepository.findByBranchIdOrderByPositionAscNameAsc(BRANCH))
                .thenReturn(List.of(amoxicilline, ciprofloxacine));

        // --- Bon validé, patient homme de 40 ans, compte-rendu DRAFT -----------------------------
        Patient patient = new Patient();
        patient.setFirstname("Koffi");
        patient.setLastname("AGBO");
        patient.setGenre("Masculin");
        patient.setAge(40);
        patient.setYearOrMonth(true);
        bon = new TestOrder();
        bon.setId(UUID.randomUUID());
        bon.setBranchId(BRANCH);
        bon.setDiscipline(Discipline.BIOLOGY);
        bon.setCode("26-0001");
        bon.setPatient(patient);
        bon.setPrelevementDate(LocalDate.now());
        for (LabTest t : List.of(nfs, uroculture)) {
            DetailTestOrder d = new DetailTestOrder();
            d.setLabTest(t);
            d.setTestName(t.getName());
            bon.getDetails().add(d);
        }
        when(testOrderRepository.findByIdAndBranchId(bon.getId(), BRANCH)).thenReturn(Optional.of(bon));
        report = new Report();
        report.setId(UUID.randomUUID());
        report.setBranchId(BRANCH);
        report.setStatus(ReportStatus.DRAFT);
        when(reportRepository.findByTestOrderId(bon.getId())).thenReturn(Optional.of(report));
        for (LabTest t : List.of(nfs, uroculture)) {
            BiologyAnalysisResult a = new BiologyAnalysisResult();
            a.setBranchId(BRANCH);
            a.setTestOrderId(bon.getId());
            a.setLabTestId(t.getId());
            analyses.save(a);
        }
    }

    private LabTest analyse(String nom, BiologyKind nature) {
        LabTest t = new LabTest();
        t.setId(UUID.randomUUID());
        t.setBranchId(BRANCH);
        t.setName(nom);
        t.setDiscipline(Discipline.BIOLOGY);
        t.setBiologyKind(nature);
        when(labTestRepository.findByIdAndBranchId(t.getId(), BRANCH)).thenReturn(Optional.of(t));
        return t;
    }

    private BiologyParameter parametre(String nom, ResultType type, Short decimals, int position) {
        BiologyParameter p = new BiologyParameter();
        p.setId(UUID.randomUUID());
        p.setLabTest(nfs);
        p.setName(nom);
        p.setResultType(type);
        p.setDecimals(decimals);
        p.setPosition(position);
        return p;
    }

    private static BiologyReferenceRange plage(BiologyParameter p, Character sexe, String low, String high,
                                               String cl, String ch) {
        BiologyReferenceRange r = new BiologyReferenceRange();
        r.setId(UUID.randomUUID());
        r.setParameter(p);
        r.setSex(sexe);
        r.setLow(low == null ? null : new BigDecimal(low));
        r.setHigh(high == null ? null : new BigDecimal(high));
        r.setCriticalLow(cl == null ? null : new BigDecimal(cl));
        r.setCriticalHigh(ch == null ? null : new BigDecimal(ch));
        return r;
    }

    private Antibiotic antibiotique(String nom, int position) {
        Antibiotic a = new Antibiotic();
        a.setId(UUID.randomUUID());
        a.setBranchId(BRANCH);
        a.setName(nom);
        a.setPosition(position);
        return a;
    }

    private static BiologyPanelResultsRequestDto.Value v(BiologyParameter p, String valeur, String manuel) {
        BiologyPanelResultsRequestDto.Value v = new BiologyPanelResultsRequestDto.Value();
        v.setParameterId(p.getId());
        v.setValue(valeur);
        v.setFlagOverride(manuel);
        return v;
    }

    private static BiologyPanelResultsRequestDto fiche(BiologyPanelResultsRequestDto.Value... valeurs) {
        BiologyPanelResultsRequestDto dto = new BiologyPanelResultsRequestDto();
        dto.setValues(new ArrayList<>(List.of(valeurs)));
        return dto;
    }

    private BiologyWorksheetDto saisir(BiologyPanelResultsRequestDto.Value... valeurs) {
        return service.savePanel(bon.getId(), nfs.getId(), fiche(valeurs), USER, BRANCH);
    }

    private BiologyParameterResult valeurDe(BiologyParameter p) {
        return valeurs.lignes.stream().filter(v -> v.getParameterId().equals(p.getId())).findFirst().orElseThrow();
    }

    private BiologyAnalysisResult ligneDe(LabTest t) {
        return analyses.lignes.stream().filter(a -> a.getLabTestId().equals(t.getId())).findFirst().orElseThrow();
    }

    private void modeUneEtape() {
        SettingApp s = new SettingApp();
        s.setValue("ONE_STEP");
        when(settingRepo.findByKeyAndBranchId(ReglagesDeBiologie.CLE_MODE, BRANCH)).thenReturn(Optional.of(s));
    }

    private List<String> actionsJournalisees() {
        ArgumentCaptor<LogReport> c = ArgumentCaptor.forClass(LogReport.class);
        verify(logRepo, atLeastOnce()).save(c.capture());
        return c.getAllValues().stream().map(LogReport::getAction).toList();
    }

    private BiologyCultureResultsRequestDto.Isolate germe(UUID id, String nom, Object... antibiogramme) {
        BiologyCultureResultsRequestDto.Isolate g = new BiologyCultureResultsRequestDto.Isolate();
        g.setId(id);
        g.setOrganism(nom);
        g.setQuantity("10^5 UFC/mL");
        List<BiologyCultureResultsRequestDto.Antibiogram> l = new ArrayList<>();
        for (int i = 0; i < antibiogramme.length; i += 2) {
            BiologyCultureResultsRequestDto.Antibiogram a = new BiologyCultureResultsRequestDto.Antibiogram();
            a.setAntibioticId(((Antibiotic) antibiogramme[i]).getId());
            a.setInterpretation((String) antibiogramme[i + 1]);
            l.add(a);
        }
        g.setAntibiogram(l);
        return g;
    }

    // =====================================================================================
    @Nested
    @DisplayName("saisie d'une fiche")
    class Fiche {

        @Test
        @DisplayName("valeur haute (virgule décimale) : H, arrondie, unité et plage de l'homme copiées")
        void valeurHaute() {
            saisir(v(hb, "17,54", null));

            BiologyParameterResult r = valeurDe(hb);
            assertThat(r.getValueNumeric()).isEqualByComparingTo("17.5");
            assertThat(r.getValueText()).isEqualTo("17.5");
            assertThat(r.getFlag()).isEqualTo(BiologyFlag.H);
            assertThat(r.isFlagOverridden()).isFalse();
            assertThat(r.getUnitSnapshot()).isEqualTo("g/dL");
            assertThat(r.getLowSnapshot()).isEqualByComparingTo("13");
            assertThat(r.getHighSnapshot()).isEqualByComparingTo("17");
            assertThat(r.getCriticalLowSnapshot()).isEqualByComparingTo("7");
            assertThat(r.getCriticalHighSnapshot()).isEqualByComparingTo("20");
            assertThat(r.getReferenceSnapshot()).isEqualTo("13,0 – 17,0");
            assertThat(r.getBranchId()).isEqualTo(BRANCH);
        }

        @Test
        @DisplayName("valeur critique : HH ; « 17,04 » arrondi à 17,0 reste N (l'indicateur suit le chiffre imprimé)")
        void critiqueEtArrondi() {
            saisir(v(hb, "25", null));
            assertThat(valeurDe(hb).getFlag()).isEqualTo(BiologyFlag.HH);
            saisir(v(hb, "17,04", null));
            assertThat(valeurDe(hb).getFlag()).isEqualTo(BiologyFlag.N);
            assertThat(valeurs.lignes).hasSize(1);   // mise à jour, pas de doublon
            saisir(v(hb, "6,9", null));
            assertThat(valeurDe(hb).getFlag()).isEqualTo(BiologyFlag.LL);
        }

        @Test
        @DisplayName("sexe inconnu : plage par défaut ; les copies suivent la plage appliquée")
        void plageParDefaut() {
            bon.getPatient().setGenre(null);
            saisir(v(hb, "16,5", null));
            BiologyParameterResult r = valeurDe(hb);
            assertThat(r.getFlag()).isEqualTo(BiologyFlag.H);
            assertThat(r.getHighSnapshot()).isEqualByComparingTo("16");
            assertThat(r.getCriticalHighSnapshot()).isNull();
        }

        @Test
        @DisplayName("sans plage : pas d'indicateur, texte de référence du catalogue copié")
        void sansPlage() {
            saisir(v(glu, "1,2345", null));
            BiologyParameterResult r = valeurDe(glu);
            assertThat(r.getValueNumeric()).isEqualByComparingTo("1.23");
            assertThat(r.getFlag()).isNull();
            assertThat(r.getReferenceSnapshot()).isEqualTo("Selon contexte");
            assertThat(r.getLowSnapshot()).isNull();
        }

        @Test
        @DisplayName("choix : forme du catalogue retenue, A manuel permis ; hors choix refusé")
        void choix() {
            saisir(v(aspect, " trouble ", "A"));
            BiologyParameterResult r = valeurDe(aspect);
            assertThat(r.getValueText()).isEqualTo("Trouble");
            assertThat(r.getValueNumeric()).isNull();
            assertThat(r.getFlag()).isEqualTo(BiologyFlag.A);
            assertThat(r.isFlagOverridden()).isTrue();

            assertThatThrownBy(() -> saisir(v(aspect, "Laiteux", null)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("ne fait pas partie des choix");
        }

        @Test
        @DisplayName("indicateur manuel sur un chiffré : remplace le calcul et est marqué")
        void manuelChiffre() {
            saisir(v(hb, "15", "L"));
            assertThat(valeurDe(hb).getFlag()).isEqualTo(BiologyFlag.L);
            assertThat(valeurDe(hb).isFlagOverridden()).isTrue();
            // Renvoyé sans indicateur manuel : on revient au calcul.
            saisir(v(hb, "15", null));
            assertThat(valeurDe(hb).getFlag()).isEqualTo(BiologyFlag.N);
            assertThat(valeurDe(hb).isFlagOverridden()).isFalse();
        }

        @Test
        @DisplayName("non signalable : indicateur manuel refusé, rien n'est enregistré")
        void nonSignalable() {
            assertThatThrownBy(() -> saisir(v(remarque, "RAS", "A")))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("ne porte pas d'indicateur");
            assertThat(ligneDe(nfs).getStatus()).isEqualTo(BiologyAnalysisStatus.PENDING);
        }

        @Test
        @DisplayName("valeur illisible, paramètre étranger ou envoyé deux fois : refusé")
        void refus() {
            assertThatThrownBy(() -> saisir(v(hb, "1.234,5", null)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("n'est pas un nombre");
            BiologyParameter etranger = parametre("Autre", ResultType.NUMERIC, null, 9);
            assertThatThrownBy(() -> saisir(v(etranger, "1", null)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("n'appartient pas");
            assertThatThrownBy(() -> saisir(v(hb, "1", null), v(hb, "2", null)))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("deux fois");
        }

        @Test
        @DisplayName("ENTERED dès une valeur, auteur et commentaire posés, journalisé ; tout effacé → PENDING")
        void etats() {
            BiologyPanelResultsRequestDto dto = fiche(v(hb, "15", null), v(remarque, "RAS", null));
            dto.setComment("  Contrôle à J7  ");
            BiologyWorksheetDto feuille = service.savePanel(bon.getId(), nfs.getId(), dto, USER, BRANCH);

            BiologyAnalysisResult l = ligneDe(nfs);
            assertThat(l.getStatus()).isEqualTo(BiologyAnalysisStatus.ENTERED);
            assertThat(l.getEnteredBy()).isEqualTo(USER);
            assertThat(l.getEnteredAt()).isNotNull();
            assertThat(l.getComment()).isEqualTo("Contrôle à J7");
            assertThat(actionsJournalisees()).contains(BiologyResultServiceImpl.ACTION_SAISIE);
            BiologyWorksheetDto.Analysis a = feuille.analyses().get(0);
            assertThat(a.enteredValues()).isEqualTo(2);
            assertThat(a.expectedValues()).isEqualTo(4);
            assertThat(a.enteredByName()).isEqualTo("DOSSOU Rita");

            saisir(v(hb, "", null), v(remarque, "  ", null));
            assertThat(valeurs.lignes).isEmpty();
            assertThat(ligneDe(nfs).getStatus()).isEqualTo(BiologyAnalysisStatus.PENDING);
            assertThat(ligneDe(nfs).getEnteredBy()).isNull();
        }

        @Test
        @DisplayName("un paramètre absent de la requête est laissé tel quel")
        void partiel() {
            saisir(v(hb, "15", null));
            saisir(v(glu, "1", null));
            assertThat(valeurs.lignes).extracting(BiologyParameterResult::getParameterId)
                    .containsExactlyInAnyOrder(hb.getId(), glu.getId());
        }

        @Test
        @DisplayName("une culture ne se saisit pas comme une fiche")
        void mauvaiseNature() {
            assertThatThrownBy(() -> service.savePanel(bon.getId(), uroculture.getId(), fiche(), USER, BRANCH))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("n'est pas de nature PANEL");
        }
    }

    // =====================================================================================
    @Nested
    @DisplayName("verrous")
    class Verrous {

        @Test
        @DisplayName("analyse validée techniquement : saisie refusée")
        void valideeTechniquement() {
            ligneDe(nfs).setStatus(BiologyAnalysisStatus.TECH_VALIDATED);
            assertThatThrownBy(() -> saisir(v(hb, "15", null)))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("annulez la validation technique");
            assertThat(valeurs.lignes).isEmpty();
        }

        @Test
        @DisplayName("compte-rendu validé ou livré : saisie, culture et (dé)validation refusées")
        void compteRenduValide() {
            for (ReportStatus s : new ReportStatus[]{ReportStatus.VALIDATED, ReportStatus.DELIVERED}) {
                report.setStatus(s);
                assertThatThrownBy(() -> saisir(v(hb, "15", null)))
                        .isInstanceOf(InvalidOperationException.class).hasMessageContaining("compte-rendu");
                assertThatThrownBy(() -> service.saveCulture(bon.getId(), uroculture.getId(),
                        new BiologyCultureResultsRequestDto(), USER, BRANCH))
                        .isInstanceOf(InvalidOperationException.class);
                assertThatThrownBy(() -> service.validateTechnically(bon.getId(), nfs.getId(), USER, BRANCH))
                        .isInstanceOf(InvalidOperationException.class);
                assertThatThrownBy(() -> service.cancelTechnicalValidation(bon.getId(), nfs.getId(), USER, BRANCH))
                        .isInstanceOf(InvalidOperationException.class);
            }
        }

        @Test
        @DisplayName("feuille : analyse non modifiable une fois validée techniquement ou le compte-rendu validé")
        void editable() {
            assertThat(service.worksheet(bon.getId(), BRANCH).analyses()).allMatch(BiologyWorksheetDto.Analysis::editable);
            ligneDe(nfs).setStatus(BiologyAnalysisStatus.TECH_VALIDATED);
            assertThat(service.worksheet(bon.getId(), BRANCH).analyses())
                    .extracting(BiologyWorksheetDto.Analysis::editable).containsExactly(false, true);
            report.setStatus(ReportStatus.VALIDATED);
            assertThat(service.worksheet(bon.getId(), BRANCH).analyses()).noneMatch(BiologyWorksheetDto.Analysis::editable);
        }

        @Test
        @DisplayName("bon d'anatomie pathologique, bon non validé, bon d'une autre succursale, analyse absente du bon")
        void bons() {
            bon.setDiscipline(Discipline.PATHOLOGY);
            assertThatThrownBy(() -> service.worksheet(bon.getId(), BRANCH))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("anatomie pathologique");
            bon.setDiscipline(Discipline.BIOLOGY);
            bon.setCode(null);
            assertThatThrownBy(() -> service.worksheet(bon.getId(), BRANCH))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("pas encore validé");
            bon.setCode("26-0001");
            assertThatThrownBy(() -> service.worksheet(bon.getId(), UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> service.savePanel(bon.getId(), UUID.randomUUID(), fiche(), USER, BRANCH))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =====================================================================================
    @Nested
    @DisplayName("validation technique et compte-rendu")
    class Validation {

        private void toutSaisir() {
            saisir(v(hb, "15", null));
            BiologyCultureResultsRequestDto c = new BiologyCultureResultsRequestDto();
            BiologyCultureResultsRequestDto.OptionValue o = new BiologyCultureResultsRequestDto.OptionValue();
            o.setCultureOptionId(optionAspect.getId());
            o.setValue("Clair");
            c.setOptions(List.of(o));
            service.saveCulture(bon.getId(), uroculture.getId(), c, USER, BRANCH);
        }

        @Test
        @DisplayName("TWO_STEP : tout saisi ne suffit pas ; tout validé → PENDING_REVIEW ; une dévalidation → DRAFT")
        void deuxEtapes() {
            toutSaisir();
            assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);

            service.validateTechnically(bon.getId(), nfs.getId(), USER, BRANCH);
            assertThat(ligneDe(nfs).getStatus()).isEqualTo(BiologyAnalysisStatus.TECH_VALIDATED);
            assertThat(ligneDe(nfs).getTechValidatedBy()).isEqualTo(USER);
            assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);

            BiologyWorksheetDto feuille = service.validateTechnically(bon.getId(), uroculture.getId(), USER, BRANCH);
            assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING_REVIEW);
            assertThat(feuille.report().status()).isEqualTo(ReportStatus.PENDING_REVIEW);

            service.cancelTechnicalValidation(bon.getId(), nfs.getId(), USER, BRANCH);
            assertThat(ligneDe(nfs).getStatus()).isEqualTo(BiologyAnalysisStatus.ENTERED);
            assertThat(ligneDe(nfs).getTechValidatedBy()).isNull();
            assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);

            assertThat(actionsJournalisees()).contains(
                    BiologyResultServiceImpl.ACTION_VALIDATION_TECHNIQUE,
                    BiologyResultServiceImpl.ACTION_ANNULATION_VALIDATION,
                    AvancementDuCompteRendu.ACTION_PRET,
                    AvancementDuCompteRendu.ACTION_RETOUR);
        }

        @Test
        @DisplayName("TWO_STEP : valider une analyse sans résultat est refusé ; revalider ne change rien")
        void deuxEtapes_refus() {
            assertThatThrownBy(() -> service.validateTechnically(bon.getId(), nfs.getId(), USER, BRANCH))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("rien à valider");
            saisir(v(hb, "15", null));
            service.validateTechnically(bon.getId(), nfs.getId(), USER, BRANCH);
            var premiere = ligneDe(nfs).getTechValidatedAt();
            service.validateTechnically(bon.getId(), nfs.getId(), USER, BRANCH);
            assertThat(ligneDe(nfs).getTechValidatedAt()).isEqualTo(premiere);
        }

        @Test
        @DisplayName("ONE_STEP : tout saisi → PENDING_REVIEW ; la validation technique est sans effet")
        void uneEtape() {
            modeUneEtape();
            saisir(v(hb, "15", null));
            assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);
            toutSaisir();
            assertThat(report.getStatus()).isEqualTo(ReportStatus.PENDING_REVIEW);

            BiologyWorksheetDto feuille = service.validateTechnically(bon.getId(), nfs.getId(), USER, BRANCH);
            assertThat(ligneDe(nfs).getStatus()).isEqualTo(BiologyAnalysisStatus.ENTERED);
            assertThat(feuille.settings().validationMode()).isEqualTo(BiologyValidationMode.ONE_STEP);

            // Une valeur effacée entièrement : l'analyse repasse PENDING, le compte-rendu DRAFT.
            saisir(v(hb, "", null));
            assertThat(report.getStatus()).isEqualTo(ReportStatus.DRAFT);
        }

        @Test
        @DisplayName("ONE_STEP : une analyse validée avant le changement de mode reste déverrouillable")
        void uneEtape_devalidation() {
            modeUneEtape();
            ligneDe(nfs).setStatus(BiologyAnalysisStatus.TECH_VALIDATED);
            service.cancelTechnicalValidation(bon.getId(), nfs.getId(), USER, BRANCH);
            assertThat(ligneDe(nfs).getStatus()).isEqualTo(BiologyAnalysisStatus.ENTERED);
        }

        @Test
        @DisplayName("annuler une validation qui n'existe pas : sans effet, rien de journalisé")
        void annulationSansObjet() {
            service.cancelTechnicalValidation(bon.getId(), nfs.getId(), USER, BRANCH);
            verify(logRepo, never()).save(any());
        }
    }

    // =====================================================================================
    @Nested
    @DisplayName("culture")
    class Culture {

        private BiologyWorksheetDto enregistrer(List<BiologyCultureResultsRequestDto.Isolate> isolats) {
            BiologyCultureResultsRequestDto dto = new BiologyCultureResultsRequestDto();
            dto.setIsolates(isolats);
            return service.saveCulture(bon.getId(), uroculture.getId(), dto, USER, BRANCH);
        }

        @Test
        @DisplayName("option, germe et antibiogramme créés ; ENTERED ; feuille ordonnée")
        void creation() {
            BiologyCultureResultsRequestDto dto = new BiologyCultureResultsRequestDto();
            BiologyCultureResultsRequestDto.OptionValue o = new BiologyCultureResultsRequestDto.OptionValue();
            o.setCultureOptionId(optionAspect.getId());
            o.setValue("trouble");
            dto.setOptions(List.of(o));
            dto.setIsolates(List.of(germe(null, " Escherichia coli ", ciprofloxacine, "r", amoxicilline, "S")));
            dto.setComment("Culture positive");

            BiologyWorksheetDto feuille = service.saveCulture(bon.getId(), uroculture.getId(), dto, USER, BRANCH);

            assertThat(cultures.lignes).singleElement().satisfies(c -> assertThat(c.getValue()).isEqualTo("Trouble"));
            assertThat(germes.lignes).singleElement().satisfies(g -> {
                assertThat(g.getOrganism()).isEqualTo("Escherichia coli");
                assertThat(g.getPosition()).isZero();
            });
            assertThat(antibiogrammes.lignes).extracting(BiologyAntibiogramResult::getInterpretation)
                    .containsExactlyInAnyOrder('R', 'S');
            assertThat(ligneDe(uroculture).getStatus()).isEqualTo(BiologyAnalysisStatus.ENTERED);
            assertThat(actionsJournalisees()).contains(BiologyResultServiceImpl.ACTION_SAISIE_CULTURE);

            BiologyWorksheetDto.Analysis a = feuille.analyses().get(1);
            assertThat(a.kind()).isEqualTo(BiologyKind.CULTURE);
            assertThat(a.comment()).isEqualTo("Culture positive");
            assertThat(a.cultureOptions()).singleElement().satisfies(r -> assertThat(r.value()).isEqualTo("Trouble"));
            assertThat(a.isolates()).singleElement().satisfies(g ->
                    assertThat(g.antibiogram()).extracting(BiologyWorksheetDto.Antibiogram::antibioticName)
                            .containsExactly("Amoxicilline", "Ciprofloxacine"));
            assertThat(feuille.antibiotics()).hasSize(2);
            assertThat(feuille.settings().antibiogramLabels()).containsEntry("S", "Sensible");
        }

        @Test
        @DisplayName("mise à jour : germe gardé par id, antibiogramme remplacé, germe absent supprimé avec son antibiogramme")
        void miseAJour() {
            enregistrer(List.of(germe(null, "E. coli", amoxicilline, "S", ciprofloxacine, "S"),
                    germe(null, "Klebsiella", amoxicilline, "R")));
            BiologyIsolate ecoli = germes.lignes.get(0);
            BiologyIsolate klebsiella = germes.lignes.get(1);

            enregistrer(List.of(germe(ecoli.getId(), "Escherichia coli", amoxicilline, "I")));

            assertThat(germes.lignes).containsExactly(ecoli);
            assertThat(ecoli.getOrganism()).isEqualTo("Escherichia coli");
            assertThat(antibiogrammes.lignes).singleElement().satisfies(a -> {
                assertThat(a.getIsolate()).isSameAs(ecoli);
                assertThat(a.getAntibioticId()).isEqualTo(amoxicilline.getId());
                assertThat(a.getInterpretation()).isEqualTo('I');
            });
            assertThat(antibiogrammes.lignes).noneMatch(a -> a.getIsolate() == klebsiella);

            enregistrer(List.of());
            assertThat(germes.lignes).isEmpty();
            assertThat(antibiogrammes.lignes).isEmpty();
            assertThat(ligneDe(uroculture).getStatus()).isEqualTo(BiologyAnalysisStatus.PENDING);
        }

        @Test
        @DisplayName("refus : interprétation hors S/I/R, antibiotique inconnu, germe étranger, option non retenue ou hors choix")
        void refus() {
            assertThatThrownBy(() -> enregistrer(List.of(germe(null, "E. coli", amoxicilline, "Sensible"))))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("S, I ou R");
            Antibiotic inconnu = antibiotique("Autre", 5);
            assertThatThrownBy(() -> enregistrer(List.of(germe(null, "E. coli", inconnu, "S"))))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("introuvable");
            assertThatThrownBy(() -> enregistrer(List.of(germe(null, "E. coli", amoxicilline, "S", amoxicilline, "R"))))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("deux fois");
            assertThatThrownBy(() -> enregistrer(List.of(germe(UUID.randomUUID(), "E. coli"))))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("n'appartient pas");

            BiologyCultureResultsRequestDto dto = new BiologyCultureResultsRequestDto();
            BiologyCultureResultsRequestDto.OptionValue o = new BiologyCultureResultsRequestDto.OptionValue();
            o.setCultureOptionId(UUID.randomUUID());
            o.setValue("x");
            dto.setOptions(List.of(o));
            assertThatThrownBy(() -> service.saveCulture(bon.getId(), uroculture.getId(), dto, USER, BRANCH))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("n'est pas retenue");
            o.setCultureOptionId(optionAspect.getId());
            o.setValue("Hématique");
            assertThatThrownBy(() -> service.saveCulture(bon.getId(), uroculture.getId(), dto, USER, BRANCH))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("ne fait pas partie des choix");
        }

        @Test
        @DisplayName("une fiche ne se saisit pas comme une culture")
        void mauvaiseNature() {
            assertThatThrownBy(() -> service.saveCulture(bon.getId(), nfs.getId(),
                    new BiologyCultureResultsRequestDto(), USER, BRANCH))
                    .isInstanceOf(BusinessException.class).hasMessageContaining("n'est pas de nature CULTURE");
        }
    }

    // =====================================================================================
    @Nested
    @DisplayName("feuille de saisie")
    class Feuille {

        @Test
        @DisplayName("patient (sexe, âge en jours), plage résolue, réglages, analyses dans l'ordre du bon")
        void feuille() {
            saisir(v(hb, "18", null));
            BiologyWorksheetDto f = service.worksheet(bon.getId(), BRANCH);

            assertThat(f.orderCode()).isEqualTo("26-0001");
            assertThat(f.patient().sex()).isEqualTo("M");
            assertThat(f.patient().ageUnit()).isEqualTo("YEARS");
            assertThat(f.patient().ageInDays()).isBetween(40 * 365, 40 * 366);
            assertThat(f.report().id()).isEqualTo(report.getId());
            assertThat(f.settings().validationMode()).isEqualTo(BiologyValidationMode.TWO_STEP);
            assertThat(f.settings().flagLabels()).containsEntry("HH", "Critique haut");
            assertThat(f.analyses()).extracting(BiologyWorksheetDto.Analysis::labTestName)
                    .containsExactly("NFS", "Uroculture");

            BiologyWorksheetDto.ParameterRow ligneHb = f.analyses().get(0).parameters().get(0);
            assertThat(ligneHb.name()).isEqualTo("Hémoglobine");
            assertThat(ligneHb.unit()).isEqualTo("g/dL");
            assertThat(ligneHb.range().low()).isEqualByComparingTo("13");
            assertThat(ligneHb.range().display()).isEqualTo("13,0 – 17,0");
            assertThat(ligneHb.result().flag()).isEqualTo(BiologyFlag.H);
            assertThat(ligneHb.result().referenceSnapshot()).isEqualTo("13,0 – 17,0");
            // Plages des paramètres non chiffrés : jamais résolues.
            assertThat(f.analyses().get(0).parameters().get(2).range()).isNull();
        }

        @Test
        @DisplayName("valeur d'un paramètre retiré du catalogue : reste visible")
        void orpheline() {
            saisir(v(hb, "15", null));
            when(parameterRepository.findByLabTest_IdOrderByPositionAsc(nfs.getId())).thenReturn(List.of(glu));
            BiologyWorksheetDto f = service.worksheet(bon.getId(), BRANCH);
            assertThat(f.analyses().get(0).parameters())
                    .anySatisfy(p -> {
                        assertThat(p.name()).isEqualTo("Paramètre retiré du catalogue");
                        assertThat(p.result().value()).isEqualTo("15.0");
                    });
        }

        @Test
        @DisplayName("paramètre retiré du catalogue : son nom d'origine est relu sur la ligne supprimée")
        void orphelineNommee() {
            saisir(v(hb, "15", null));
            when(parameterRepository.findByLabTest_IdOrderByPositionAsc(nfs.getId())).thenReturn(List.of(glu));
            BiologyParameterRepository.LibelleDeParametre libelle = mock(BiologyParameterRepository.LibelleDeParametre.class);
            when(libelle.getId()).thenReturn(hb.getId().toString());
            when(libelle.getName()).thenReturn("Hémoglobine");
            when(parameterRepository.findLibellesYComprisRetires(List.of(hb.getId()))).thenReturn(List.of(libelle));

            BiologyWorksheetDto f = service.worksheet(bon.getId(), BRANCH);

            assertThat(f.analyses().get(0).parameters())
                    .anySatisfy(p -> {
                        assertThat(p.parameterId()).isEqualTo(hb.getId());
                        assertThat(p.name()).isEqualTo("Hémoglobine");
                        assertThat(p.result().value()).isEqualTo("15.0");
                    });
        }
    }

    // =====================================================================================
    @Nested
    @DisplayName("liste de travail")
    class ListeDeTravail {

        @Test
        @DisplayName("état inconnu ou dates inversées : refusés ; bornes de dates inclusives")
        void filtres() {
            assertThatThrownBy(() -> service.worklist("FINI", null, null, null, 0, 20, BRANCH))
                    .isInstanceOf(BusinessException.class);
            assertThatThrownBy(() -> service.worklist(null, null, LocalDate.of(2026, 2, 1),
                    LocalDate.of(2026, 1, 1), 0, 20, BRANCH)).isInstanceOf(BusinessException.class);

            when(analysisRepo.findWorklist(any(), any(), any(), any(), any(), any()))
                    .thenReturn(org.springframework.data.domain.Page.empty());
            service.worklist("entered", null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), 0, 500, BRANCH);
            ArgumentCaptor<org.springframework.data.domain.Pageable> page =
                    ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
            verify(analysisRepo).findWorklist(eq(BRANCH), eq("ENTERED"), eq(null),
                    eq(LocalDate.of(2026, 1, 1).atStartOfDay()), eq(LocalDate.of(2026, 2, 1).atStartOfDay()),
                    page.capture());
            assertThat(page.getValue().getPageSize()).isEqualTo(100);
        }
    }
}

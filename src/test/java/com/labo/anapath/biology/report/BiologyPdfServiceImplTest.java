package com.labo.anapath.biology.report;

import com.labo.anapath.biology.results.BiologyAnalysisStatus;
import com.labo.anapath.biology.results.BiologyFlag;
import com.labo.anapath.biology.results.BiologyResultService;
import com.labo.anapath.biology.results.BiologyWorksheetDto;
import com.labo.anapath.biology.results.ReglagesDeBiologie;
import com.labo.anapath.common.Discipline;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.doctor.Doctor;
import com.labo.anapath.doctor.Hospital;
import com.labo.anapath.report.LogReport;
import com.labo.anapath.report.LogReportRepository;
import com.labo.anapath.report.PdfReportServiceImpl;
import com.labo.anapath.report.QrCodeService;
import com.labo.anapath.report.Report;
import com.labo.anapath.report.ReportRepository;
import com.labo.anapath.report.ReportStatus;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import com.labo.anapath.test.LabTestRepository;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.labo.anapath.biology.report.FeuillesDeTest.ANTIBIOGRAMME;
import static com.labo.anapath.biology.report.FeuillesDeTest.INDICATEURS;
import static com.labo.anapath.biology.report.FeuillesDeTest.ab;
import static com.labo.anapath.biology.report.FeuillesDeTest.culture;
import static com.labo.anapath.biology.report.FeuillesDeTest.feuille;
import static com.labo.anapath.biology.report.FeuillesDeTest.germe;
import static com.labo.anapath.biology.report.FeuillesDeTest.grandPanel;
import static com.labo.anapath.biology.report.FeuillesDeTest.nombre;
import static com.labo.anapath.biology.report.FeuillesDeTest.option;
import static com.labo.anapath.biology.report.FeuillesDeTest.panel;
import static com.labo.anapath.biology.report.FeuillesDeTest.parametre;
import static com.labo.anapath.biology.report.FeuillesDeTest.section;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * PDF du compte-rendu de biologie : règles d'impression (provisoire, discipline,
 * succursale), et rendus réels ouverts avec PDFBox.
 *
 * <p>Avec {@code -Dpdf.dump=<dossier>}, les PDF produits sont écrits dans ce
 * dossier pour être regardés.</p>
 */
class BiologyPdfServiceImplTest {

    private static final UUID BRANCH = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();

    private final ReportRepository reportRepository = mock(ReportRepository.class);
    private final LogReportRepository logRepo = mock(LogReportRepository.class);
    private final SettingAppRepository settingRepo = mock(SettingAppRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final LabTestRepository labTestRepository = mock(LabTestRepository.class);
    private final BiologyResultService resultService = mock(BiologyResultService.class);
    private final ReglagesDeBiologie reglages = mock(ReglagesDeBiologie.class);
    private final SpringTemplateEngine moteur = moteur();

    private final BiologyPdfServiceImpl service = new BiologyPdfServiceImpl(reportRepository, logRepo, settingRepo,
            userRepository, labTestRepository, new QrCodeService(), moteur, resultService, reglages);

    private Report report;
    private TestOrder bon;

    private static SpringTemplateEngine moteur() {
        ClassLoaderTemplateResolver resolveur = new ClassLoaderTemplateResolver();
        resolveur.setPrefix("templates/");
        resolveur.setSuffix(".html");
        resolveur.setTemplateMode(TemplateMode.HTML);
        resolveur.setCharacterEncoding("UTF-8");
        SpringTemplateEngine m = new SpringTemplateEngine();
        m.setTemplateResolver(resolveur);
        return m;
    }

    @BeforeEach
    void preparer() {
        Doctor medecin = new Doctor();
        medecin.setName("Dr HOUNSA Paul");
        Hospital hopital = new Hospital();
        hopital.setName("CNHU Cotonou");
        bon = new TestOrder();
        bon.setId(UUID.randomUUID());
        bon.setBranchId(BRANCH);
        bon.setCode("26-0100");
        bon.setDiscipline(Discipline.BIOLOGY);
        bon.setDoctor(medecin);
        bon.setHospital(hopital);
        bon.setPrelevementDate(LocalDate.of(2026, 9, 1));

        User biologiste = new User();
        biologiste.setFirstname("Awa");
        biologiste.setLastname("BIOLOGISTE");
        biologiste.setSignature("Admin_Admin.png");

        report = new Report();
        report.setId(UUID.randomUUID());
        report.setBranchId(BRANCH);
        report.setCode("CO26-0100");
        report.setDiscipline(Discipline.BIOLOGY);
        report.setTestOrder(bon);
        report.setStatus(ReportStatus.VALIDATED);
        report.setSignatory1(biologiste);
        report.setSignatureDate(LocalDateTime.of(2026, 9, 3, 10, 0));
        report.setCreatedAt(LocalDateTime.of(2026, 9, 1, 8, 0));

        when(reportRepository.findById(report.getId())).thenReturn(Optional.of(report));
        when(settingRepo.findByKey(anyString())).thenReturn(Optional.empty());
        SettingApp pied = new SettingApp();
        pied.setKey("report_footer");
        pied.setValue("Laboratoire de biologie • Cotonou • www.labo.bj");
        when(settingRepo.findByKey("report_footer")).thenReturn(Optional.of(pied));
        when(reglages.impressionProvisoire(BRANCH)).thenReturn(true);
        when(reglages.libellesIndicateurs(BRANCH)).thenReturn(INDICATEURS);
        when(reglages.libellesAntibiogramme(BRANCH)).thenReturn(ANTIBIOGRAMME);
        when(labTestRepository.findCategoriesYComprisRetirees(any())).thenReturn(List.of());
    }

    private void feuilleDuBon(List<BiologyWorksheetDto.Analysis> analyses) {
        when(resultService.worksheet(bon.getId(), BRANCH)).thenReturn(feuille(analyses));
    }

    private static String texte(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            // Espaces multiples ramenés à un : le moteur en pose autour des éléments en ligne.
            return new PDFTextStripper().getText(doc).replaceAll("[ \\t]+", " ");
        }
    }

    private static int pages(byte[] pdf) throws Exception {
        try (PDDocument doc = PDDocument.load(pdf)) {
            return doc.getNumberOfPages();
        }
    }

    private static void deposer(String nom, byte[] pdf) throws Exception {
        String dossier = System.getProperty("pdf.dump");
        if (dossier != null && !dossier.isBlank()) {
            Files.write(Files.createDirectories(Path.of(dossier)).resolve(nom), pdf);
        }
    }

    private static int occurrences(String texte, String motif) {
        int n = 0;
        for (int i = texte.indexOf(motif); i >= 0; i = texte.indexOf(motif, i + motif.length())) {
            n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ règles

    @Test
    @DisplayName("non validé et bio_print_provisional=false → 422, rien n'est rendu ni journalisé")
    void provisoireInterdit() {
        report.setStatus(ReportStatus.PENDING_REVIEW);
        when(reglages.impressionProvisoire(BRANCH)).thenReturn(false);

        assertThatThrownBy(() -> service.generatePdf(report.getId(), USER, BRANCH))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("provisoires");
        verifyNoInteractions(resultService);
        verify(logRepo, never()).save(any());
    }

    @Test
    @DisplayName("compte-rendu d'anatomie pathologique → refus ; autre succursale → 404")
    void disciplineEtSuccursale() {
        assertThatThrownBy(() -> service.generatePdf(report.getId(), USER, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
        report.setDiscipline(Discipline.PATHOLOGY);
        assertThatThrownBy(() -> service.generatePdf(report.getId(), USER, BRANCH))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(resultService);
    }

    @Test
    @DisplayName("GET /reports/{id}/pdf : un compte-rendu de biologie est aiguillé vers ce rendu")
    void aiguillage() {
        BiologyPdfService biologie = mock(BiologyPdfService.class);
        when(biologie.generatePdf(report.getId(), USER, null)).thenReturn(new byte[] {1, 2, 3});
        // Le cœur d'anatomie pathologique charge le compte-rendu dans l'agence de la requête.
        when(reportRepository.findByIdAndBranchId(org.mockito.ArgumentMatchers.eq(report.getId()), org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(report));
        PdfReportServiceImpl pathologie = new PdfReportServiceImpl(reportRepository, logRepo, settingRepo,
                userRepository, new QrCodeService(), moteur, biologie);

        assertThat(pathologie.generatePdf(report.getId(), USER)).containsExactly(1, 2, 3);
    }

    // ------------------------------------------------------------------ rendus réels

    @Test
    @DisplayName("validé : catégories, tableau, indicateurs, commentaire, conclusion, signature ; journal « Imprimer »")
    void valide() throws Exception {
        BiologyWorksheetDto.Analysis nfs = panel("Numération formule sanguine", BiologyAnalysisStatus.TECH_VALIDATED,
                "Anisocytose modérée.",
                List.of(nombre("Hémoglobine", "18.3", BiologyFlag.H, "g/dL", "13,0 – 17,0"),
                        nombre("Plaquettes", "250", BiologyFlag.N, "G/L", "150 – 400"),
                        parametre("Calcul interne", "7", new java.math.BigDecimal("7"), BiologyFlag.N, null, null,
                                false)),
                List.of(section("Formule leucocytaire",
                        nombre("Neutrophiles", "0.4", BiologyFlag.LL, "G/L", "≥ 1,5"))));
        feuilleDuBon(List.of(nfs));
        LabTestRepository.CategorieDAnalyse cat = mock(LabTestRepository.CategorieDAnalyse.class);
        when(cat.getLabTestId()).thenReturn(nfs.labTestId().toString());
        when(cat.getCategoryName()).thenReturn("Hématologie");
        when(labTestRepository.findCategoriesYComprisRetirees(any())).thenReturn(List.of(cat));
        report.setComment("Bilan à contrôler dans un mois.");

        byte[] pdf = service.generatePdf(report.getId(), USER, BRANCH);
        deposer("biologie-valide.pdf", pdf);
        String t = texte(pdf);

        assertThat(t).contains("RÉSULTATS D'ANALYSES DE BIOLOGIE MÉDICALE", "N° dossier : 26-0100",
                "Date validation: 03/09/2026", "AGBO", "Koffi", "40 ans", "Dr HOUNSA Paul", "CNHU Cotonou");
        assertThat(t).contains("HÉMATOLOGIE", "Paramètre", "Résultat", "Unité", "Valeurs de référence");
        assertThat(t).contains("Hémoglobine 18,3 (Haut) g/dL 13,0 – 17,0");
        assertThat(t).contains("Neutrophiles 0,4 (Critique bas) G/L ≥ 1,5");
        assertThat(t).contains("Formule leucocytaire", "Commentaire : Anisocytose modérée.");
        assertThat(t).doesNotContain("Calcul interne", "PROVISOIRES");
        assertThat(t).contains("CONCLUSION", "Bilan à contrôler dans un mois.");
        assertThat(t).contains("BIOLOGISTE Awa", "Laboratoire de biologie • Cotonou • www.labo.bj");

        ArgumentCaptor<LogReport> journal = ArgumentCaptor.forClass(LogReport.class);
        verify(logRepo).save(journal.capture());
        assertThat(journal.getValue().getAction()).isEqualTo("Imprimer");
        assertThat(journal.getValue().getDescription()).isEqualTo("PDF généré pour le rapport CO26-0100");
    }

    @Test
    @DisplayName("fiche longue : le tableau se poursuit page suivante, en-tête répété")
    void plusieursPages() throws Exception {
        feuilleDuBon(List.of(
                panel("Glycémie", BiologyAnalysisStatus.TECH_VALIDATED, null,
                        List.of(nombre("Glucose", "1.02", BiologyFlag.N, "g/L", "0,70 – 1,10")), List.of()),
                grandPanel("Bilan étendu", 90)));

        byte[] pdf = service.generatePdf(report.getId(), USER, BRANCH);
        deposer("biologie-plusieurs-pages.pdf", pdf);
        String t = texte(pdf);

        assertThat(pages(pdf)).isGreaterThanOrEqualTo(3);
        assertThat(t).contains("Paramètre n° 1 ", "Paramètre n° 90 ", "Seconde partie");
        // En-tête du tableau répété sur chaque page où il se poursuit.
        assertThat(occurrences(t, "Valeurs de référence")).isGreaterThanOrEqualTo(pages(pdf) - 1);
        // Pied de page sur chaque page.
        assertThat(occurrences(t, "Laboratoire de biologie • Cotonou")).isEqualTo(pages(pdf));
        assertThat(t).contains("Paramètre n° 7 7,5 (Haut)", "Paramètre n° 11 11,5 (Critique bas)");
    }

    @Test
    @DisplayName("culture : options, germes, antibiogramme aux libellés du laboratoire et légende")
    void cultureAvecAntibiogramme() throws Exception {
        feuilleDuBon(List.of(culture("Examen cytobactériologique des urines",
                List.of(option("Aspect", "Trouble"), option("Leucocytes", "> 100 000 /mL")),
                List.of(germe("Escherichia coli", "10^5 UFC/mL",
                                ab("Amoxicilline", "R", ">32", null),
                                ab("Amoxicilline + acide clavulanique", "I", "16", null),
                                ab("Ciprofloxacine", "S", "0,25", "25.4"),
                                ab("Ceftriaxone", "S", null, "30")),
                        germe("Enterococcus faecalis", "10^4 UFC/mL",
                                ab("Ampicilline", "S", null, null),
                                ab("Vancomycine", "S", null, null))))));

        byte[] pdf = service.generatePdf(report.getId(), USER, BRANCH);
        deposer("biologie-culture-antibiogramme.pdf", pdf);
        String t = texte(pdf);

        assertThat(t).contains("Aspect : Trouble", "Leucocytes : > 100 000 /mL");
        assertThat(t).contains("Germe 1 : Escherichia coli — 10^5 UFC/mL", "Germe 2 : Enterococcus faecalis");
        assertThat(t).contains("Antibiotique", "Interprétation", "CMI", "Diamètre (mm)");
        assertThat(t).contains("Amoxicilline R Résistant >32", "Ciprofloxacine S Sensible 0,25 25,4");
        assertThat(t).contains("Antibiogramme : S = Sensible ; I = Intermédiaire ; R = Résistant");
    }

    @Test
    @DisplayName("provisoire : bandeau et rappel en pied de page, ni signature ni date de validation")
    void provisoire() throws Exception {
        report.setStatus(ReportStatus.PENDING_REVIEW);
        report.setSignatureDate(null);
        List<BiologyWorksheetDto.ParameterRow> lignes = new ArrayList<>();
        lignes.add(nombre("CRP", "48", BiologyFlag.H, "mg/L", "≤ 6"));
        feuilleDuBon(List.of(
                panel("Protéine C réactive", BiologyAnalysisStatus.ENTERED, null, lignes, List.of()),
                panel("Ionogramme", BiologyAnalysisStatus.PENDING, null, List.of(), List.of())));

        byte[] pdf = service.generatePdf(report.getId(), USER, BRANCH);
        deposer("biologie-provisoire.pdf", pdf);
        String t = texte(pdf);

        assertThat(occurrences(t, "RÉSULTATS PROVISOIRES")).isEqualTo(1 + pages(pdf));
        assertThat(t).contains("CRP 48 (Haut) mg/L ≤ 6", "Ionogramme", "Résultat en attente");
        assertThat(t).containsPattern("Date validation: ?\\R").doesNotContain("BIOLOGISTE Awa");

        ArgumentCaptor<LogReport> journal = ArgumentCaptor.forClass(LogReport.class);
        verify(logRepo).save(journal.capture());
        assertThat(journal.getValue().getDescription()).startsWith("PDF provisoire");
    }
}

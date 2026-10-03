package com.labo.anapath.report;

import com.labo.anapath.doctor.Doctor;
import com.labo.anapath.doctor.Hospital;
import com.labo.anapath.patient.Patient;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Rendu réel du compte-rendu d'anatomie pathologique, du service au PDF.
 *
 * <p>Filet de sécurité de l'extraction de {@code PdfAssets} : le service est
 * branché sur le vrai gabarit, le vrai moteur Thymeleaf et le vrai générateur de
 * QR code, avec des dates fixes. Le texte extrait doit porter chaque rubrique du
 * document — entête, patient, contenu, signature, pied de page.</p>
 *
 * <p>Avec {@code -Dpdf.dump=<dossier>}, le PDF et son texte sont écrits dans ce
 * dossier : c'est ce qui a servi à comparer le rendu avant et après extraction.</p>
 */
class RenduPdfAnatomiePathologiqueTest {

    private static final UUID REPORT_ID = UUID.fromString("00000000-0000-0000-0000-00000000b701");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-00000000b702");

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

    private static Report compteRendu() {
        Patient patient = new Patient();
        patient.setFirstname("Awa");
        patient.setLastname("KORA");
        patient.setAge(42);
        patient.setYearOrMonth(true);
        patient.setGenre("Féminin");

        Doctor medecin = new Doctor();
        medecin.setName("Dr HOUNSA Paul");
        Hospital hopital = new Hospital();
        hopital.setName("CNHU Cotonou");

        TestOrder bon = new TestOrder();
        bon.setCode("26-0042");
        bon.setPatient(patient);
        bon.setDoctor(medecin);
        bon.setHospital(hopital);
        bon.setPrelevementDate(LocalDate.of(2026, 9, 1));
        bon.setTestAffiliate("REF-77");

        User signataire = new User();
        signataire.setFirstname("Jean");
        signataire.setLastname("TAKIN");
        signataire.setSignature("Admin_Admin.png");
        User relecteur = new User();
        relecteur.setFirstname("Marie");
        relecteur.setLastname("AHOMADEGBE");

        TitleReport titre = new TitleReport();
        titre.setName("Biopsie gastrique");

        Report r = new Report();
        r.setId(REPORT_ID);
        r.setCode("CO26-0042");
        r.setBranchId(UUID.randomUUID());
        r.setTestOrder(bon);
        r.setTitleReport(titre);
        r.setContent("<p><b>Macroscopie</b> : trois fragments de 2 à 4 mm.</p>");
        r.setContentMicro("<ul><li>Muqueuse antrale</li><li>Absence de dysplasie</li></ul>");
        r.setStatus(ReportStatus.VALIDATED);
        r.setSignatory1(signataire);
        r.setReviewedBy(relecteur);
        r.setSignatureDate(LocalDateTime.of(2026, 9, 3, 10, 15));
        r.setCreatedAt(LocalDateTime.of(2026, 9, 2, 8, 0));
        return r;
    }

    private static SettingApp reglage(String cle, String valeur) {
        SettingApp s = new SettingApp();
        s.setKey(cle);
        s.setValue(valeur);
        return s;
    }

    @Test
    @DisplayName("Le compte-rendu d'anatomie pathologique sort avec toutes ses rubriques")
    void rendu() throws Exception {
        ReportRepository reports = mock(ReportRepository.class);
        SettingAppRepository reglages = mock(SettingAppRepository.class);
        UserRepository users = mock(UserRepository.class);
        when(reports.findById(REPORT_ID)).thenReturn(Optional.of(compteRendu()));
        when(reglages.findByKey(anyString())).thenReturn(Optional.empty());
        when(reglages.findByKey("report_footer"))
                .thenReturn(Optional.of(reglage("report_footer", "Centre ADECHINA • Cotonou • www.caap.bj")));
        when(users.findById(any())).thenReturn(Optional.empty());

        PdfReportServiceImpl service = new PdfReportServiceImpl(reports, mock(LogReportRepository.class),
                reglages, users, new QrCodeService(), moteur(),
                mock(com.labo.anapath.biology.report.BiologyPdfService.class));

        byte[] pdf = service.generatePdf(REPORT_ID, USER_ID);
        String texte;
        try (PDDocument doc = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            texte = new PDFTextStripper().getText(doc);
        }

        assertThat(texte)
                .contains("N° ANAPTH :", "26-0042", "Examen reference", "REF-77")
                .contains("Date validation: 03/09/2026")
                .contains("BIOPSIE GASTRIQUE")
                .contains("KORA", "Awa", "42 ans", "Féminin", "01/09/2026", "02/09/2026")
                .contains("CNHU Cotonou", "Dr HOUNSA Paul")
                .contains("trois fragments", "Absence de dysplasie")
                .contains("TAKIN Jean", "Signé électroniquement par :", "AHOMADEGBE Marie")
                .contains("Centre ADECHINA • Cotonou • www.caap.bj");

        String dossier = System.getProperty("pdf.dump");
        if (dossier != null && !dossier.isBlank()) {
            Path d = Files.createDirectories(Path.of(dossier));
            Files.write(d.resolve("pathologie.pdf"), pdf);
            // La date d'impression est celle du jour : on la neutralise pour
            // comparer deux rendus faits à des jours différents.
            String stable = texte.replace(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    "<aujourd'hui>");
            Files.writeString(d.resolve("pathologie.txt"), stable, StandardCharsets.UTF_8);
        }
    }
}

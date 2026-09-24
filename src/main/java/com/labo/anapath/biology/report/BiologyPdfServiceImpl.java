package com.labo.anapath.biology.report;

import com.labo.anapath.biology.results.BiologyResultService;
import com.labo.anapath.biology.results.BiologyWorksheetDto;
import com.labo.anapath.biology.results.ReglagesDeBiologie;
import com.labo.anapath.common.Discipline;
import com.labo.anapath.common.NomComplet;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.pdf.PdfAssets;
import com.labo.anapath.common.pdf.PdfFonts;
import com.labo.anapath.common.pdf.PdfHtmlUtil;
import com.labo.anapath.report.LogReport;
import com.labo.anapath.report.LogReportRepository;
import com.labo.anapath.report.QrCodeService;
import com.labo.anapath.report.Report;
import com.labo.anapath.report.ReportRepository;
import com.labo.anapath.report.ReportStatus;
import com.labo.anapath.setting.SettingAppRepository;
import com.labo.anapath.test.LabTestRepository;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.user.UserRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implémentation de {@link BiologyPdfService}.
 *
 * <p>Même entête, même bloc patient, même QR code, même signature et même pied de
 * page que le compte-rendu d'anatomie pathologique ({@link PdfAssets}) ; le corps
 * est fait des résultats de la feuille de saisie, mis en forme par
 * {@link CompteRenduDeBiologie}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BiologyPdfServiceImpl implements BiologyPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Titre du bandeau. */
    static final String TITRE = "Résultats d'analyses de biologie médicale";

    /**
     * Police du document, embarquée : les valeurs de référence portent « ≥ » et
     * « ≤ », absents des polices de base du PDF (Helvetica, celle du compte-rendu
     * d'anatomie pathologique).
     */
    static final String POLICE_DE_REPLI = "DejaVu Sans";

    private final ReportRepository reportRepository;
    private final LogReportRepository logReportRepository;
    private final SettingAppRepository settingAppRepository;
    private final UserRepository userRepository;
    private final LabTestRepository labTestRepository;
    private final QrCodeService qrCodeService;
    private final SpringTemplateEngine templateEngine;
    private final BiologyResultService biologyResultService;
    private final ReglagesDeBiologie reglages;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public byte[] generatePdf(UUID reportId, UUID userId, UUID branchId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", reportId));
        if (branchId != null && !branchId.equals(report.getBranchId())) {
            throw new ResourceNotFoundException("Compte-rendu", reportId);
        }
        if (report.getDiscipline() != Discipline.BIOLOGY) {
            throw new BusinessException("Ce compte-rendu relève de l'anatomie pathologique : "
                    + "il s'imprime depuis ses propres écrans.");
        }
        TestOrder bon = report.getTestOrder();
        if (bon == null) {
            throw new BusinessException("Ce compte-rendu n'est rattaché à aucun bon d'examen.");
        }

        boolean valide = report.getStatus() == ReportStatus.VALIDATED
                || report.getStatus() == ReportStatus.DELIVERED;
        if (!valide && !reglages.impressionProvisoire(report.getBranchId())) {
            throw new BusinessException("Le compte-rendu n'est pas encore validé, et l'impression "
                    + "des résultats provisoires est désactivée pour ce laboratoire.");
        }

        BiologyWorksheetDto feuille = biologyResultService.worksheet(bon.getId(), report.getBranchId());
        CompteRenduDeBiologie document = CompteRenduDeBiologie.construire(feuille,
                categories(feuille), report.getComment(), !valide,
                reglages.libellesIndicateurs(report.getBranchId()),
                reglages.libellesAntibiogramme(report.getBranchId()));

        Context ctx = contexte(report, bon, feuille, document, valide);
        String html = PdfHtmlUtil.toXhtml(templateEngine.process("pdf/biologie", ctx));

        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            PdfFonts.enregistrer(builder, "pdf-assets/fonts/DejaVuSans.ttf", POLICE_DE_REPLI, 400);
            PdfFonts.enregistrer(builder, "pdf-assets/fonts/DejaVuSans-Bold.ttf", POLICE_DE_REPLI, 700);
            builder.withHtmlContent(html, null);
            builder.toStream(sortie);
            builder.run();

            LogReport trace = new LogReport();
            trace.setBranchId(report.getBranchId());
            trace.setReport(report);
            trace.setAction("Imprimer");
            trace.setDescription((valide ? "PDF généré pour le rapport " : "PDF provisoire généré pour le rapport ")
                    + report.getCode());
            if (userId != null) {
                userRepository.findById(userId).ifPresent(trace::setUser);
            }
            logReportRepository.save(trace);

            return sortie.toByteArray();
        } catch (Exception e) {
            throw new InvalidOperationException("Erreur lors de la génération du PDF: " + e.getMessage());
        }
    }

    /** Nom de catégorie de chaque analyse de la feuille, analyses retirées comprises. */
    private Map<UUID, String> categories(BiologyWorksheetDto feuille) {
        List<UUID> ids = feuille.analyses().stream().map(BiologyWorksheetDto.Analysis::labTestId).toList();
        Map<UUID, String> resultat = new HashMap<>();
        if (ids.isEmpty()) {
            return resultat;
        }
        for (LabTestRepository.CategorieDAnalyse c : labTestRepository.findCategoriesYComprisRetirees(ids)) {
            if (c != null && c.getLabTestId() != null && c.getCategoryName() != null) {
                resultat.put(UUID.fromString(c.getLabTestId()), c.getCategoryName());
            }
        }
        return resultat;
    }

    /**
     * Variables du gabarit. L'entête et le bloc patient reprennent les noms de
     * {@code pdf/rapport} : les deux gabarits partagent ces blocs.
     */
    Context contexte(Report report, TestOrder bon, BiologyWorksheetDto feuille, CompteRenduDeBiologie document,
                     boolean valide) {
        Context ctx = new Context();
        ctx.setVariable("code", report.getCode());
        ctx.setVariable("title", TITRE);
        ctx.setVariable("document", document);
        ctx.setVariable("testOrderCode", bon.getCode() != null ? bon.getCode() : "");
        ctx.setVariable("testAffiliate", bon.getTestAffiliate() != null ? bon.getTestAffiliate() : "");
        ctx.setVariable("signatureDate",
                valide && report.getSignatureDate() != null ? report.getSignatureDate().format(DATE_FMT) : "");
        ctx.setVariable("prelevementDate",
                bon.getPrelevementDate() != null ? bon.getPrelevementDate().format(DATE_FMT) : "");
        ctx.setVariable("currentDate", LocalDate.now().format(DATE_FMT));
        ctx.setVariable("createdAt",
                report.getCreatedAt() != null ? report.getCreatedAt().format(DATE_FMT) : "");

        String qrCode = "";
        try {
            if (bon.getCode() != null) {
                qrCode = qrCodeService.generateBase64(bon.getCode(), 200);
            }
        } catch (Exception e) {
            log.warn("QR code generation failed: {}", e.getMessage());
        }
        ctx.setVariable("qrcode", qrCode);

        // Patient : âge exprimé comme sur la feuille de saisie (une unité non
        // renseignée vaut « ans » en biologie, voir AgeDuPatient).
        BiologyWorksheetDto.Patient patient = feuille.patient();
        ctx.setVariable("patientFirstname", patient != null && patient.firstname() != null ? patient.firstname() : "");
        ctx.setVariable("patientLastname", patient != null && patient.lastname() != null ? patient.lastname() : "");
        ctx.setVariable("patientAge", patient != null && patient.age() != null ? patient.age() : "");
        ctx.setVariable("patientAgeUnit", patient != null && "MONTHS".equals(patient.ageUnit()) ? "mois" : "ans");
        ctx.setVariable("patientGenre", patient != null && patient.genre() != null ? patient.genre() : "");

        ctx.setVariable("doctorName", bon.getDoctor() != null && bon.getDoctor().getName() != null
                ? bon.getDoctor().getName() : "");
        ctx.setVariable("hospitalName", bon.getHospital() != null && bon.getHospital().getName() != null
                ? bon.getHospital().getName() : "");

        // Signature : seulement sur un compte-rendu validé, jamais sur un provisoire.
        boolean signe = valide && report.getSignatory1() != null;
        ctx.setVariable("status", valide ? 1 : 0);
        ctx.setVariable("signator", signe
                ? NomComplet.de(report.getSignatory1().getLastname(), report.getSignatory1().getFirstname()) : "");
        ctx.setVariable("signature1Img", signe ? PdfAssets.signature(report.getSignatory1()) : "");

        ctx.setVariable("enteteImg", PdfAssets.entete(settingAppRepository));
        ctx.setVariable("footer", PdfAssets.piedDePage(settingAppRepository));
        return ctx;
    }
}

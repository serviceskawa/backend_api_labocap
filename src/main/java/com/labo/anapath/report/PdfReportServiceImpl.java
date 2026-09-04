package com.labo.anapath.report;

import com.labo.anapath.common.NomComplet;

import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import com.labo.anapath.user.UserRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfReportServiceImpl implements PdfReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReportRepository reportRepository;
    private final LogReportRepository logReportRepository;
    private final SettingAppRepository settingAppRepository;
    private final UserRepository userRepository;
    private final QrCodeService qrCodeService;
    private final SpringTemplateEngine templateEngine;

    @Override
    @Transactional
    public byte[] generatePdf(UUID reportId, UUID userId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu", reportId));

        Context ctx = new Context();

        ctx.setVariable("code", report.getCode());
        ctx.setVariable("testOrderCode",
                report.getTestOrder() != null ? report.getTestOrder().getCode() : "");
        ctx.setVariable("signatureDate",
                report.getSignatureDate() != null ? report.getSignatureDate().format(DATE_FMT) : "");
        ctx.setVariable("prelevementDate",
                report.getTestOrder() != null && report.getTestOrder().getPrelevementDate() != null
                        ? report.getTestOrder().getPrelevementDate().format(DATE_FMT) : "");
        ctx.setVariable("testAffiliate",
                report.getTestOrder() != null ? report.getTestOrder().getTestAffiliate() : "");

        // QR Code
        String qrCode = "";
        try {
            if (report.getTestOrder() != null) {
                qrCode = qrCodeService.generateBase64(report.getTestOrder().getCode(), 200);
            }
        } catch (Exception e) {
            log.warn("QR code generation failed: {}", e.getMessage());
        }
        ctx.setVariable("qrcode", qrCode);

        ctx.setVariable("title",
                report.getTitleReport() != null ? report.getTitleReport().getName() : "");
        ctx.setVariable("content", report.getContent() != null ? report.getContent() : "");
        ctx.setVariable("contentMicro", report.getContentMicro() != null ? report.getContentMicro() : "");
        ctx.setVariable("contentSupplementaire",
                report.getDescriptionSupplementaire() != null ? report.getDescriptionSupplementaire() : "");
        ctx.setVariable("contentSupplementaireMicro",
                report.getDescriptionSupplementaireMicro() != null ? report.getDescriptionSupplementaireMicro() : "");

        // Patient
        if (report.getTestOrder() != null && report.getTestOrder().getPatient() != null) {
            var patient = report.getTestOrder().getPatient();
            ctx.setVariable("patientFirstname", patient.getFirstname());
            ctx.setVariable("patientLastname", patient.getLastname());
            ctx.setVariable("patientAge", patient.getAge() != null ? patient.getAge() : "");
            ctx.setVariable("patientAgeUnit",
                    Boolean.TRUE.equals(patient.getYearOrMonth()) ? "ans" : "mois");
            ctx.setVariable("patientGenre", patient.getGenre() != null ? patient.getGenre() : "");
        } else {
            ctx.setVariable("patientFirstname", "");
            ctx.setVariable("patientLastname", "");
            ctx.setVariable("patientAge", "");
            ctx.setVariable("patientAgeUnit", "ans");
            ctx.setVariable("patientGenre", "");
        }

        // Signataires
        if (report.getSignatory1() != null) {
            ctx.setVariable("signator",
                    NomComplet.de(report.getSignatory1().getLastname(), report.getSignatory1().getFirstname()));
            ctx.setVariable("signature1", report.getSignatory1().getSignature() != null
                    ? report.getSignatory1().getSignature() : "");
        } else {
            ctx.setVariable("signator", "");
            ctx.setVariable("signature1", "");
        }
        ctx.setVariable("signatory2Name", report.getSignatory2() != null
                ? NomComplet.de(report.getSignatory2().getLastname(), report.getSignatory2().getFirstname()) : "");
        ctx.setVariable("signatory3Name", report.getSignatory3() != null
                ? NomComplet.de(report.getSignatory3().getLastname(), report.getSignatory3().getFirstname()) : "");
        ctx.setVariable("reviewedBy", report.getReviewedBy() != null
                ? NomComplet.de(report.getReviewedBy().getLastname(), report.getReviewedBy().getFirstname()) : "");

        // Médecin et hôpital
        ctx.setVariable("doctorName", report.getTestOrder() != null && report.getTestOrder().getDoctor() != null
                ? report.getTestOrder().getDoctor().getName() : "");
        ctx.setVariable("hospitalName", report.getTestOrder() != null && report.getTestOrder().getHospital() != null
                ? report.getTestOrder().getHospital().getName() : "");

        // Dates (impression = aujourd'hui ; arrivée labo = date de création du CR)
        ctx.setVariable("currentDate", LocalDate.now().format(DATE_FMT));
        ctx.setVariable("createdAt",
                report.getCreatedAt() != null ? report.getCreatedAt().format(DATE_FMT) : "");

        // Statut : 1 = validé/livré → affiche les signatures (comme Laravel $data['status'] == 1)
        boolean validated = report.getStatus() == ReportStatus.VALIDATED
                || report.getStatus() == ReportStatus.DELIVERED;
        ctx.setVariable("status", validated ? 1 : 0);

        // Entête image du laboratoire : priorité au setting `entete` uploadé
        // (data URI base64, comme entete_pdf_cr.png dans Laravel) ; repli sur le
        // placeholder embarqué dans les ressources si aucun n'a été téléversé.
        String enteteSetting = settingAppRepository.findByKey("entete")
                .map(SettingApp::getValue).orElse("");
        String enteteImg = (enteteSetting != null && enteteSetting.startsWith("data:"))
                ? enteteSetting
                : loadImageDataUri("pdf-assets/entete_pdf_cr.png");
        ctx.setVariable("enteteImg", enteteImg);

        // Settings
        ctx.setVariable("footer", settingAppRepository.findByKey("report_footer")
                .map(SettingApp::getValue).filter(v -> !v.isBlank())
                .orElse(SettingApp.DEFAULT_REPORT_FOOTER));
        // Titre de revue : « Signé électroniquement par : » par défaut (réplique du
        // rendu de référence CAAP), surchargé par le réglage report_review_title si défini.
        String reviewTitle = settingAppRepository.findByKey("report_review_title")
                .map(SettingApp::getValue).filter(v -> !v.isBlank())
                .orElse("Signé électroniquement par :");
        ctx.setVariable("reportReviewTitle", reviewTitle);

        // Image de signature du signataire 1 (embarquée si le fichier est disponible)
        String signature1Img = "";
        if (report.getSignatory1() != null
                && report.getSignatory1().getSignature() != null
                && !report.getSignatory1().getSignature().isBlank()) {
            signature1Img = loadImageDataUri("pdf-assets/signatures/" + report.getSignatory1().getSignature());
        }
        ctx.setVariable("signature1Img", signature1Img);

        // Render HTML puis normalisation XHTML (contenu éditeur → OpenHTMLToPDF)
        String html = com.labo.anapath.common.pdf.PdfHtmlUtil.toXhtml(
                templateEngine.process("pdf/rapport", ctx));

        // Convert to PDF
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            // Le compte rendu est le seul document dont le rédacteur choisit la
            // police. Sans ces substituts, tout choix sortant des quatorze
            // polices de base du PDF retombait sur du Times.
            com.labo.anapath.common.pdf.PdfFonts.enregistrerSubstituts(builder);
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();

            // Log
            LogReport logReport = new LogReport();
            logReport.setBranchId(report.getBranchId());
            logReport.setReport(report);
            logReport.setAction("Imprimer");
            logReport.setDescription("PDF généré pour le rapport " + report.getCode());
            userRepository.findById(userId).ifPresent(logReport::setUser);
            logReportRepository.save(logReport);

            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new InvalidOperationException("Erreur lors de la génération du PDF: " + e.getMessage());
        }
    }

    /**
     * Charge une image depuis le classpath et la renvoie en data URI base64
     * (embarquée dans le HTML, pour qu'OpenHTMLToPDF la rende sans baseUri).
     * Renvoie une chaîne vide si l'image est absente.
     */
    private String loadImageDataUri(String classpathLocation) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathLocation);
            if (!resource.exists()) {
                return "";
            }
            byte[] bytes;
            try (InputStream in = resource.getInputStream()) {
                bytes = in.readAllBytes();
            }
            String mime = classpathLocation.toLowerCase().endsWith(".jpg")
                    || classpathLocation.toLowerCase().endsWith(".jpeg")
                    ? "image/jpeg" : "image/png";
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("Chargement image PDF échoué ({}): {}", classpathLocation, e.getMessage());
            return "";
        }
    }
}

package com.labo.anapath.finance;

import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.report.QrCodeService;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import com.labo.anapath.setting.SettingInvoice;
import com.labo.anapath.setting.SettingInvoiceRepository;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
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
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoicePdfServiceImpl implements InvoicePdfService {

    /**
     * Format de date du PDF. Reproduit l'interpolation Blade {@code {{$invoice->created_at}}} :
     * le modèle Laravel ne déclare aucun {@code $casts}, donc Carbon est converti en chaîne
     * via son format par défaut, horodatage compris.
     */
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Note d'information en pied de reçu (identique au reçu à l'écran). */
    private static final String NOTE_IMPORTANTE =
            "Les résultats de vos analyses seront disponibles dans un délai de 3 semaines. "
            + "Selon la complexité du cas, les résultats peuvent être disponibles plus tôt ou plus tard. "
            + "Vous serez notifiés dès que les résultats seront prêts. Nous vous remercions de votre "
            + "compréhension et de votre patience.";

    private final InvoiceRepository invoiceRepository;
    private final SettingInvoiceRepository settingInvoiceRepository;
    private final SettingAppRepository settingAppRepository;
    private final QrCodeService qrCodeService;
    private final SpringTemplateEngine templateEngine;
    private final com.labo.anapath.user.UserRepository userRepository;

    /** Ligne de facture rendue dans le template PDF. */
    public record PdfLine(int index, String testName, int quantity, String price,
                          String discount, String total) {}

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID invoiceId, UUID branchId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", invoiceId));
        if (!invoice.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Facture", invoiceId);
        }

        // Configuration facture (normalisation MECeF) — équivaut à SettingInvoice::first() de Laravel
        SettingInvoice settingInvoice = settingInvoiceRepository
                .findFirstByBranchId(branchId)
                .orElse(null);
        boolean invoiceNormalised = settingInvoice != null && Boolean.TRUE.equals(settingInvoice.getStatus());

        boolean isAvoir = invoice.getStatusInvoice() == 1;

        Context ctx = new Context();

        // Titre du document et libellé du bloc « facture de vente / d'avoir ».
        ctx.setVariable("docTitle", isAvoir ? "Facture d'avoir" : "Facture de vente");

        // Logo du laboratoire (réglage `logo`, base64), affiché sur la ligne d'en-tête.
        String logo = settingAppRepository.findByKey("logo").map(SettingApp::getValue).orElse("");
        ctx.setVariable("logo", logo != null && logo.startsWith("data:") ? logo : "");

        ctx.setVariable("code", invoice.getCode() != null ? invoice.getCode() : "");
        ctx.setVariable("codeStatus", Boolean.TRUE.equals(invoice.getPaid()) ? "[Payé]" : "[En attente]");
        ctx.setVariable("date", invoice.getCreatedAt() != null ? invoice.getCreatedAt().format(DATE_FMT) : "");
        // Référence : code de la facture d'origine pour un avoir, vide pour une vente.
        ctx.setVariable("reference",
                invoice.getReference() != null && invoice.getReference().getCode() != null
                        ? invoice.getReference().getCode() : "");
        ctx.setVariable("codeNormalise", invoice.getCodeNormalise() != null ? invoice.getCodeNormalise() : "");

        // Client (dénormalisé sur la facture, repli sur le patient).
        String clientName = invoice.getClientName();
        if ((clientName == null || clientName.isBlank()) && invoice.getPatient() != null) {
            clientName = (invoice.getPatient().getFirstname() + " " + invoice.getPatient().getLastname()).trim();
        }
        ctx.setVariable("clientName", clientName != null ? clientName : "");
        // Adresse dénormalisée de la facture (la facture imprimée affiche `client_address`).
        ctx.setVariable("clientAddress", invoice.getClientAddress() != null ? invoice.getClientAddress() : "");
        ctx.setVariable("patientCode",
                invoice.getPatient() != null && invoice.getPatient().getCode() != null
                        ? invoice.getPatient().getCode() : "");
        // Demande d'examen sans tiret (helper Laravel remove_hyphen).
        ctx.setVariable("demandeExamen",
                invoice.getTestOrder() != null && invoice.getTestOrder().getCode() != null
                        ? invoice.getTestOrder().getCode().replace("-", "") : "");

        // Lignes : Prix = prix catalogue, Remise et Total, formatés comme le reçu.
        List<PdfLine> lines = new ArrayList<>();
        int i = 1;
        for (InvoiceDetail d : invoice.getDetails()) {
            lines.add(new PdfLine(
                    i++,
                    d.getTestName() != null ? d.getTestName() : "",
                    1,
                    money(d.getPrice()),
                    money(d.getDiscount()),
                    money(d.getTotal())));
        }
        ctx.setVariable("lines", lines);

        ctx.setVariable("subtotal", money(invoice.getSubtotal()));
        ctx.setVariable("total", money(invoice.getTotal()));

        // Signature : nom de l'opérateur (créateur de la facture) et, si disponible en
        // base64, son image. Les signatures migrées sont des chemins de fichiers non
        // intégrables : dans ce cas seul le nom s'affiche.
        String operatorName = "";
        String signatureImage = "";
        if (invoice.getCreatedBy() != null) {
            var creator = userRepository.findById(invoice.getCreatedBy()).orElse(null);
            if (creator != null) {
                operatorName = (creator.getFirstname() + " " + creator.getLastname()).trim();
                if (creator.getSignature() != null && creator.getSignature().startsWith("data:")) {
                    signatureImage = creator.getSignature();
                }
            }
        }
        ctx.setVariable("operatorName", operatorName);
        ctx.setVariable("signatureImage", signatureImage);

        ctx.setVariable("noteImportante", NOTE_IMPORTANTE);
        ctx.setVariable("reportFooter",
                settingAppRepository.findByKey("report_footer").map(SettingApp::getValue)
                        .filter(v -> !v.isBlank()).orElse(SettingApp.DEFAULT_REPORT_FOOTER));

        // QR de l'en-tête, toujours présent sur la facture imprimée. Encode le même
        // contenu que le reçu à l'écran : le code normalisé, ou le nom du centre à défaut.
        ctx.setVariable("headerQrcode", buildHeaderQrcode(invoice));

        String html = com.labo.anapath.common.pdf.PdfHtmlUtil.toXhtml(
                templateEngine.process("pdf/facture", ctx));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            registerNunito(builder);
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new InvalidOperationException("Erreur lors de la génération du PDF de la facture : " + e.getMessage());
        }
    }

    /**
     * Embarque Nunito dans le PDF.
     *
     * <p>OpenHTMLToPDF ne connaît que les 14 polices de base du format PDF : sans cet
     * enregistrement, la famille déclarée dans la feuille de style est ignorée et le
     * document est rendu en Helvetica, avec d'autres métriques et d'autres retours à la
     * ligne que la page imprimée par le navigateur côté Laravel.</p>
     */
    private void registerNunito(PdfRendererBuilder builder) {
        registerFont(builder, "pdf-assets/fonts/Nunito-Regular.ttf", 400);
        registerFont(builder, "pdf-assets/fonts/Nunito-SemiBold.ttf", 600);
        registerFont(builder, "pdf-assets/fonts/Nunito-Bold.ttf", 700);
    }

    private void registerFont(PdfRendererBuilder builder, String classpathLocation, int weight) {
        ClassPathResource resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            log.warn("Police PDF absente ({}) : rendu en police de substitution.", classpathLocation);
            return;
        }
        builder.useFont(() -> {
            try {
                return resource.getInputStream();
            } catch (Exception e) {
                throw new IllegalStateException("Lecture de la police " + classpathLocation + " impossible", e);
            }
        }, "Nunito", weight, BaseRendererBuilder.FontStyle.NORMAL, true);
    }

    /**
     * Formate un total à la mode Laravel {@code number_format(abs($v), 0, ',', ' ')} :
     * valeur absolue, sans décimale, milliers séparés par une espace simple (U+0020).
     *
     * <p>L'espace est forcé explicitement : {@code Locale.FRANCE} utilise l'espace fine
     * insécable (U+202F) sur les JDK récents. L'arrondi est aligné sur PHP, qui arrondit
     * au plus loin de zéro et non au pair le plus proche.</p>
     */
    /**
     * QR de l'en-tête de la facture imprimée. Même contenu encodé que le reçu à
     * l'écran (cf. {@code InvoiceServiceImpl#buildQrcode}) : le code normalisé DGI,
     * ou le nom du centre tant que la facture n'est pas normalisée.
     */
    private String buildHeaderQrcode(Invoice invoice) {
        String content = invoice.getCodeNormalise() != null && !invoice.getCodeNormalise().isBlank()
                ? invoice.getCodeNormalise()
                : "Centre ADECHINA Anatomie Pathologique";
        try {
            return qrCodeService.generateBase64(content, 100, 105, ErrorCorrectionLevel.H);
        } catch (Exception e) {
            log.warn("QR d'en-tête non généré pour la facture {} : {}", invoice.getId(), e.getMessage());
            return "";
        }
    }

    private static String money(Number value) {
        double v = value != null ? Math.abs(value.doubleValue()) : 0d;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        DecimalFormat df = new DecimalFormat("#,##0", symbols);
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df.format(v);
    }

    private static String money(BigDecimal value) {
        return money((Number) value);
    }

    /**
     * Rend une valeur telle que Blade l'imprimerait pour un float : ni séparateur de
     * milliers, ni valeur absolue, ni décimale forcée.
     *
     * <p>Les cellules «Prix» et «Total» des lignes de facture sont interpolées brutes
     * dans {@code print.blade.php} ({@code {{$item->price}}}), contrairement au bloc des
     * totaux. PHP imprime un float entier sans «.0» et null en chaîne vide.</p>
     */
    private static String rawAmount(Number value) {
        if (value == null) {
            return "";
        }
        double v = value.doubleValue();
        if (v == Math.rint(v) && !Double.isInfinite(v)) {
            return String.valueOf((long) v);
        }
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
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

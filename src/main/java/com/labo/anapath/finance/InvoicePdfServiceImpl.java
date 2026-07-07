package com.labo.anapath.finance;

import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.report.QrCodeService;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoicePdfServiceImpl implements InvoicePdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InvoiceRepository invoiceRepository;
    private final SettingAppRepository settingAppRepository;
    private final QrCodeService qrCodeService;
    private final SpringTemplateEngine templateEngine;

    /** Ligne de facture rendue dans le template PDF. */
    public record PdfLine(int index, String testName, int quantity, String unitPrice, String total) {}

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID invoiceId, UUID branchId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", invoiceId));
        if (!invoice.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Facture", invoiceId);
        }

        Context ctx = new Context();

        // En-tête / logo du laboratoire (HTML)
        ctx.setVariable("entete", settingAppRepository.findByKey("entete")
                .map(SettingApp::getValue).orElse(""));

        ctx.setVariable("title", invoice.getStatusInvoice() == 1 ? "Facture d'avoir" : "Reçu de paiement");
        ctx.setVariable("code", invoice.getCode() != null ? invoice.getCode() : "");
        ctx.setVariable("date", invoice.getCreatedAt() != null ? invoice.getCreatedAt().format(DATE_FMT) : "");
        ctx.setVariable("payment", invoice.getPayment() != null ? invoice.getPayment() : "");

        // Client (dénormalisé sur la facture, repli sur le patient)
        String clientName = invoice.getClientName();
        if ((clientName == null || clientName.isBlank()) && invoice.getPatient() != null) {
            clientName = (invoice.getPatient().getFirstname() + " " + invoice.getPatient().getLastname()).trim();
        }
        ctx.setVariable("clientName", clientName != null ? clientName : "");
        ctx.setVariable("clientAddress", invoice.getClientAddress() != null ? invoice.getClientAddress() : "");

        // Lignes
        List<PdfLine> lines = new ArrayList<>();
        int i = 1;
        for (InvoiceDetail d : invoice.getDetails()) {
            lines.add(new PdfLine(
                    i++,
                    d.getTestName() != null ? d.getTestName() : "",
                    d.getQuantity(),
                    money(d.getUnitPrice()),
                    money(d.getTotal())));
        }
        ctx.setVariable("lines", lines);

        ctx.setVariable("subtotal", money(invoice.getSubtotal()));
        ctx.setVariable("total", money(invoice.getTotal()));

        // Bloc MECeF (uniquement si la facture est normalisée)
        boolean hasMecef = invoice.getCodeMecef() != null && !invoice.getCodeMecef().isBlank();
        ctx.setVariable("hasMecef", hasMecef);
        ctx.setVariable("codeMecef", invoice.getCodeMecef() != null ? invoice.getCodeMecef() : "");
        ctx.setVariable("nim", invoice.getNim() != null ? invoice.getNim() : "");
        ctx.setVariable("counters", invoice.getCounters() != null ? invoice.getCounters() : "");
        ctx.setVariable("dateGenerate", invoice.getDateGenerate() != null ? invoice.getDateGenerate() : "");

        String qrcode = "";
        if (hasMecef) {
            try {
                qrcode = qrCodeService.generateBase64(invoice.getCodeMecef(), 160);
            } catch (Exception e) {
                log.warn("Génération du QR code facture échouée : {}", e.getMessage());
            }
        }
        ctx.setVariable("qrcode", qrcode);

        String html = templateEngine.process("pdf/facture", ctx);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new InvalidOperationException("Erreur lors de la génération du PDF de la facture : " + e.getMessage());
        }
    }

    /** Formate un montant à la mode Laravel : valeur absolue, sans décimale, milliers séparés par une espace. */
    private static String money(Number value) {
        double v = value != null ? Math.abs(value.doubleValue()) : 0d;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        DecimalFormat df = new DecimalFormat("#,##0", symbols);
        return df.format(v);
    }

    private static String money(BigDecimal value) {
        return money((Number) value);
    }
}

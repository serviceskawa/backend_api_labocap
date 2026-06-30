package com.labo.anapath.finance;

import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
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
public class CashboxDailyPdfServiceImpl implements CashboxDailyPdfService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String DASH = "-";

    private final CashboxDailyRepository cashboxDailyRepository;
    private final SettingAppRepository settingAppRepository;
    private final SpringTemplateEngine templateEngine;

    /** Ligne du récapitulatif par mode de paiement. */
    public record PdfRow(String method, String fondInitial, String vente,
                         String solde, String comptage, String ecart, boolean total) {}

    @Override
    @Transactional(readOnly = true)
    public byte[] generatePdf(UUID dailyId, UUID branchId) {
        CashboxDaily d = cashboxDailyRepository.findById(dailyId)
                .orElseThrow(() -> new ResourceNotFoundException("Journée de caisse", dailyId));
        if (!d.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Journée de caisse", dailyId);
        }

        Context ctx = new Context();
        ctx.setVariable("entete", settingAppRepository.findByKey("entete")
                .map(SettingApp::getValue).orElse(""));
        ctx.setVariable("code", d.getCode() != null ? d.getCode() : "");
        ctx.setVariable("cashboxName", d.getCashbox() != null ? d.getCashbox().getName() : "");
        ctx.setVariable("openedAt", d.getCreatedAt() != null ? d.getCreatedAt().format(DT_FMT) : "");
        ctx.setVariable("closedAt", d.getUpdatedAt() != null ? d.getUpdatedAt().format(DT_FMT) : "");

        BigDecimal opening = nz(d.getOpeningBalance());
        BigDecimal cashCalc = nz(d.getCashCalculated());
        BigDecimal soldeEspeces = opening.add(cashCalc);

        List<PdfRow> rows = new ArrayList<>();
        rows.add(new PdfRow("Espèces", money(opening), money(cashCalc), money(soldeEspeces),
                money(d.getCashConfirmation()), money(d.getCashEcart()), false));
        rows.add(new PdfRow("Mobile Money", DASH, money(d.getMobileMoneyCalculated()), DASH,
                money(d.getMoneyMoneyConfirmation()), money(d.getMobileMoneyEcart()), false));
        rows.add(new PdfRow("Chèques", DASH, money(d.getChequeCalculated()), DASH,
                money(d.getChequeConfirmation()), money(d.getChequeEcart()), false));
        rows.add(new PdfRow("Virement", DASH, money(d.getVirementCalculated()), DASH,
                money(d.getVirementConfirmation()), money(d.getVirementEcart()), false));
        rows.add(new PdfRow("Total", money(opening), money(d.getTotalCalculated()), money(soldeEspeces),
                money(d.getTotalConfirmation()), money(d.getTotalEcart()), true));
        ctx.setVariable("rows", rows);

        ctx.setVariable("closingBalance", money(d.getClosingBalance()));

        String html = templateEngine.process("pdf/cashbox-daily", ctx);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new InvalidOperationException("Erreur lors de la génération du PDF de clôture : " + e.getMessage());
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** Formate un montant : milliers séparés par une espace, sans décimale (le signe est conservé). */
    private static String money(BigDecimal value) {
        double v = value != null ? value.doubleValue() : 0d;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        DecimalFormat df = new DecimalFormat("#,##0", symbols);
        return df.format(v);
    }
}

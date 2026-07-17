package com.labo.anapath.finance;

import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CashboxDailyPdfServiceImpl implements CashboxDailyPdfService {

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String DASH = "-";

    private final CashboxDailyRepository cashboxDailyRepository;
    private final SpringTemplateEngine templateEngine;

    /** Ligne du récapitulatif par mode de paiement (calque du tableau Blade Laravel). */
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

        // En-tête : logo du laboratoire embarqué en data URI (dégrade en chaîne vide si absent),
        // + identifiant et horodatages, comme le Blade « ID {code} : {created_at} - {updated_at} ».
        ctx.setVariable("logoImg", loadImageDataUri("pdf-assets/logo.png"));
        ctx.setVariable("code", d.getCode() != null ? d.getCode() : "");
        ctx.setVariable("createdAt", d.getCreatedAt() != null ? d.getCreatedAt().format(DT_FMT) : "");
        ctx.setVariable("updatedAt", d.getUpdatedAt() != null ? d.getUpdatedAt().format(DT_FMT) : "");

        // Solde « Espèces » et « Total » : opening_balance + cash_calculated (identique au Blade).
        String soldeEspeces = solde(d.getOpeningBalance(), d.getCashCalculated());

        List<PdfRow> rows = new ArrayList<>();
        // Espèces
        rows.add(new PdfRow("Especes",
                raw(d.getOpeningBalance()), raw(d.getCashCalculated()), soldeEspeces,
                raw(d.getCashConfirmation()), raw(d.getCashEcart()), false));
        // Mobile Money
        rows.add(new PdfRow("Mobile Money",
                DASH, raw(d.getMobileMoneyCalculated()), DASH,
                raw(d.getMoneyMoneyConfirmation()), raw(d.getMobileMoneyEcart()), false));
        // Chèques — le Blade Laravel affiche total_ecart dans la colonne Écart de cette ligne
        // (on réplique fidèlement ce comportement pour un rendu identique).
        rows.add(new PdfRow("Cheques",
                DASH, raw(d.getChequeCalculated()), DASH,
                raw(d.getChequeConfirmation()), raw(d.getTotalEcart()), false));
        // Virement
        rows.add(new PdfRow("Virement",
                DASH, raw(d.getVirementCalculated()), DASH,
                raw(d.getVirementConfirmation()), raw(d.getVirementEcart()), false));
        // Total (ligne en gras)
        rows.add(new PdfRow("Total",
                raw(d.getOpeningBalance()), raw(d.getTotalCalculated()), soldeEspeces,
                raw(d.getTotalConfirmation()), raw(d.getTotalEcart()), true));
        ctx.setVariable("rows", rows);

        // Commentaire : aucune colonne équivalente côté API (cashbox_dailies n'a pas de champ
        // description/commentaire), on rend le champ vide comme un input readonly non renseigné.
        ctx.setVariable("description", "");

        // Solde de fermeture
        ctx.setVariable("closeBalance", raw(d.getClosingBalance()));

        String html = com.labo.anapath.common.pdf.PdfHtmlUtil.toXhtml(
                templateEngine.process("pdf/cashbox-daily", ctx));

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

    /**
     * Rendu brut d'un montant, comme Laravel qui affiche directement la valeur DECIMAL(12,2)
     * (ex. « 5000.00 »). Renvoie une chaîne vide si la valeur est nulle.
     */
    private static String raw(BigDecimal v) {
        return v != null ? v.toPlainString() : "";
    }

    /**
     * Somme affichée dans la colonne « Solde » (opening_balance + cash_calculated), en calquant
     * l'arithmétique PHP du Blade qui supprime les zéros décimaux non significatifs (ex. « 7000 »).
     */
    private static String solde(BigDecimal opening, BigDecimal cash) {
        BigDecimal sum = nz(opening).add(nz(cash)).stripTrailingZeros();
        return sum.toPlainString();
    }

    /**
     * Charge une image depuis le classpath et la renvoie en data URI base64 (embarquée dans le HTML,
     * pour qu'OpenHTMLToPDF la rende sans baseUri). Renvoie une chaîne vide si l'image est absente.
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

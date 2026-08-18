package com.labo.anapath.report;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.expression.ThymeleafEvaluationContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * Où atterrit le bloc de signature du compte rendu, mesuré dans le PDF produit.
 *
 * <p>Le laboratoire signale une signature renvoyée en page suivante alors que la
 * page courante paraît encore avoir de la place. Une lecture de la feuille de
 * style ne peut ni confirmer ni infirmer cela : c'est le moteur de pagination
 * qui tranche. Ce test rend le vrai gabarit avec des contenus de longueurs
 * croissantes, puis ouvre le fichier pour voir sur quelle page la signature est
 * tracée et combien de place restait au-dessus.</p>
 */
class PaginationSignatureTest {

    /** Hauteur A4 en points, et marge basse réservée au pied de page (115 px). */
    private static final double HAUTEUR_PAGE = 842.0;
    private static final double BAS_DU_CADRE = HAUTEUR_PAGE - 115.0 * 0.75;

    private static final String SIGNATAIRE = "ZZSIGNATAIREZZ";

    private final TemplateEngine moteur = creerMoteur();

    /**
     * Le moteur Spring, et non le moteur autonome : c'est celui que le serveur
     * emploie, et il évalue en SpEL là où l'autre réclamerait OGNL. Un contexte
     * applicatif vide suffit — le gabarit ne référence aucun bean.
     */
    private static TemplateEngine creerMoteur() {
        ClassLoaderTemplateResolver resolveur = new ClassLoaderTemplateResolver();
        resolveur.setPrefix("templates/");
        resolveur.setSuffix(".html");
        resolveur.setTemplateMode(TemplateMode.HTML);
        resolveur.setCharacterEncoding("UTF-8");
        SpringTemplateEngine m = new SpringTemplateEngine();
        m.setTemplateResolver(resolveur);
        return m;
    }

    private static final GenericApplicationContext CONTEXTE_SPRING = contexteVide();

    private static GenericApplicationContext contexteVide() {
        GenericApplicationContext c = new GenericApplicationContext();
        c.refresh();
        return c;
    }

    private byte[] rendre(int nombreDeLignes) throws Exception {
        StringBuilder corps = new StringBuilder();
        for (int i = 1; i <= nombreDeLignes; i++) {
            corps.append("<p>Ligne ").append(i)
                 .append(" du compte rendu, de longueur ordinaire pour un paragraphe.</p>");
        }

        Context ctx = new Context();
        ctx.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(CONTEXTE_SPRING, null));
        for (String vide : new String[] {
                "qrcode", "enteteImg", "signature1Img", "contentMicro",
                "contentSupplementaire", "contentSupplementaireMicro" }) {
            ctx.setVariable(vide, "");
        }
        ctx.setVariable("code", "26-0003");
        ctx.setVariable("testOrderCode", "26-0003");
        ctx.setVariable("title", "COMPTE RENDU");
        ctx.setVariable("content", corps.toString());
        ctx.setVariable("signatureDate", "05/08/2026");
        ctx.setVariable("prelevementDate", "01/08/2026");
        ctx.setVariable("createdAt", "02/08/2026");
        ctx.setVariable("currentDate", "05/08/2026");
        ctx.setVariable("testAffiliate", "Histologie");
        ctx.setVariable("patientFirstname", "Mesure");
        ctx.setVariable("patientLastname", "Essai");
        ctx.setVariable("patientAge", "40");
        ctx.setVariable("patientAgeUnit", "ans");
        ctx.setVariable("patientGenre", "F");
        ctx.setVariable("doctorName", "Dr Prescripteur");
        ctx.setVariable("hospitalName", "Service");
        ctx.setVariable("signator", SIGNATAIRE);
        ctx.setVariable("reviewedBy", "Dr Relecteur");
        ctx.setVariable("reportReviewTitle", "Signé électroniquement par :");
        ctx.setVariable("footer", "Pied de page du laboratoire");
        ctx.setVariable("status", 1);

        String html = com.labo.anapath.common.pdf.PdfHtmlUtil.toXhtml(moteur.process("pdf/rapport", ctx));
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            PdfRendererBuilder constructeur = new PdfRendererBuilder();
            com.labo.anapath.common.pdf.PdfFonts.enregistrerSubstituts(constructeur);
            constructeur.withHtmlContent(html, null);
            constructeur.toStream(sortie);
            constructeur.run();
            return sortie.toByteArray();
        }
    }

    /** Une ligne tracée : sa page, son texte, sa position verticale depuis le haut. */
    private record Trace(int page, String texte, double y) {}

    private List<Trace> traces(byte[] pdf) throws Exception {
        List<Trace> lues = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDFTextStripper lecteur = new PDFTextStripper() {
                @Override
                protected void writeString(String texte, List<TextPosition> positions) {
                    if (!positions.isEmpty()) {
                        lues.add(new Trace(getCurrentPageNo(), texte.trim(),
                                positions.get(0).getYDirAdj()));
                    }
                }
            };
            lecteur.setSortByPosition(true);
            lecteur.getText(doc);
        }
        return lues;
    }

    /** Les trois textes du bloc de signature, qui doivent rester ensemble. */
    private static boolean estDuBlocSignature(String texte) {
        return texte.contains(SIGNATAIRE)
                || texte.contains("Dr Relecteur")
                || texte.contains("électroniquement");
    }

    @Test
    @DisplayName("Le bloc de signature ne se coupe jamais entre deux pages")
    void blocDeSignatureJamaisCoupe() throws Exception {
        List<String> coupures = new ArrayList<>();

        for (int lignes = 1; lignes <= 60; lignes++) {
            List<Integer> pages = traces(rendre(lignes)).stream()
                    .filter(t -> estDuBlocSignature(t.texte()))
                    .map(Trace::page).distinct().toList();
            if (pages.size() > 1) {
                coupures.add(lignes + " lignes → bloc réparti sur les pages " + pages);
            }
        }

        // Mesuré avant correctif : à 20 lignes, « Signé électroniquement par : »
        // restait en page 1 pendant que le nom du signataire passait en page 2.
        assertThat(coupures)
                .describedAs("le bloc de signature a été coupé entre deux pages")
                .isEmpty();
    }

    @Test
    @DisplayName("Le blanc laissé avant la signature reste borné")
    void blancAvantLaSignatureBorne() throws Exception {
        double pireGachis = 0;
        int pireLignes = 0;
        StringBuilder journal = new StringBuilder(System.lineSeparator());

        for (int lignes = 1; lignes <= 60; lignes++) {
            List<Trace> lues = traces(rendre(lignes));
            Trace premierDuBloc = lues.stream()
                    .filter(t -> estDuBlocSignature(t.texte()))
                    .findFirst().orElseThrow();

            double basAvant = lues.stream()
                    .filter(t -> t.page() == premierDuBloc.page() - 1 && t.y() < BAS_DU_CADRE)
                    .mapToDouble(Trace::y).max().orElse(-1);
            if (basAvant < 0) continue;   // le bloc n'a chassé personne

            double placeLibre = BAS_DU_CADRE - basAvant;
            if (placeLibre > pireGachis) {
                pireGachis = placeLibre;
                pireLignes = lignes;
            }
            journal.append(String.format("  %2d lignes → %.0f pt libres avant la signature%n",
                    lignes, placeLibre));
        }

        journal.append(String.format("%n  pire blanc laissé : %.0f pt (%d lignes)%n",
                pireGachis, pireLignes));
        System.out.println(journal);

        // Un bloc qu'on refuse de couper laisse au pire sa propre hauteur en
        // blanc : c'est le prix de le garder d'un seul tenant. Mesuré à 80 pt
        // avec les 45 px réservés à la signature manuscrite et 24 px de marge.
        // Le seuil garde contre une inflation, pas contre ce prix-là.
        assertThat(pireGachis)
                .describedAs("le bloc de signature réclame plus de place qu'avant%s", journal)
                .isLessThanOrEqualTo(85);
    }
}

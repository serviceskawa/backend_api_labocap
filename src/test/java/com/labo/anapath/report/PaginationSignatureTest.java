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

    /** Signature carrée de 300 px — la forme qui gonflait le bloc. */
    private static final String SIGNATURE_CARREE = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAASwAAAEsCAIAAAD2HxkiAAADVElEQVR4nO3TMQEAAAiAMO"
            + "PYP6ExONwS8DALpKYOgO9MCDETQsyEEDMhxEwIMRNCzIQQMyHETAgxE0LMhBAzIcRMCDET"
            + "QsyEEDMhxEwIMRNCzIQQMyHETAgxE0LMhBAzIcRMCDETQsyEEDMhxEwIMRNCzIQQMyHETA"
            + "gxE0LMhBAzIcRMCDETQsyEEDMhxEwIMRNCzIQQMyHETAgxE0LMhBAzIcRMCDETQsyEEDMh"
            + "xEwIMRNCzIQQMyHETAgxE0LMhBAzIcRMCDETQsyEEDMhxEwIMRNCzIQQMyHETAgxE0LMhB"
            + "AzIcRMCDETQsyEEDMhxEwIMRNCzIQQMyHETAgxE0LMhBAzIcRMCDETQsyEEDMhxEwIMRNC"
            + "zIQQMyHETAgxE0LMhBAzIcRMCDETQsyEEDMhxEwIMRNCzIQQMyHETAgxE0LMhBAzIcRMCD"
            + "ETQsyEEDMhxEwIMRNCzIQQMyHETAgxE0LMhBAzIcRMCDETQsyEEDMhxEwIMRNCzIQQMyHE"
            + "TAgxE0LMhBAzIcRMCDETQsyEEDMhxEwIMRNCzIQQMyHETAgxE0LMhBAzIcRMCDETQsyEED"
            + "MhxEwIMRNCzIQQMyHETAgxE0LMhBAzIcRMCDETQsyEEDMhxEwIMRNCzIQQMyHETAgxE0LM"
            + "hBAzIcRMCDETQsyEEDMhxEwIMRNCzIQQMyHETAgxE0LMhBAzIcRMCDETQsyEEDMhxEwIMR"
            + "NCzIQQMyHETAgxE0LMhBAzIcRMCDETQsyEEDMhxEwIMRNCzIQQMyHETAgxE0LMhBAzIcRM"
            + "CDETQsyEEDMhxEwIMRNCzIQQMyHETAgxE0LMhBAzIcRMCDETQsyEEDMhxEwIMRNCzIQQMy"
            + "HETAgxE0LMhBAzIcRMCDETQsyEEDMhxEwIMRNCzIQQMyHETAgxE0LMhBAzIcRMCDETQsyE"
            + "EDMhxEwIMRNCzIQQMyHETAgxE0LMhBAzIcRMCDETQsyEEDMhxEwIMRNCzIQQMyHETAgxE0"
            + "LMhBAzIcRMCDETQsyEEDMhxEwIMRNCzIQQMyHETAgxE0LMhBAzIcRMCDETQsyEEDMhxEwI"
            + "MRNCzIQQMyHETAgxE0LMhBAzIcRMCDETQsyEEDMhxEwIMRNCzIQQMyHETAgxE0LsAKXM1R"
            + "22BzigAAAAAElFTkSuQmCC";

    /** Signature en bandeau, 400 x 120 px : elle ne doit pas être agrandie. */
    private static final String SIGNATURE_BANDEAU = "data:image/png;base64,"
            + "iVBORw0KGgoAAAANSUhEUgAAAZAAAAB4CAIAAABTvTPAAAABo0lEQVR4nO3UMQ0AMAzAsM"
            + "Ipf4SlsG+KZCPIlVmAiPkdAPDKsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIM"
            + "C8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDA"
            + "vIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwL"
            + "yDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8"
            + "gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvI"
            + "MCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyD"
            + "AsIMOwgAzDAjIMC8gwLCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIMC8gw"
            + "LCDDsIAMwwIyDAvIMCwgw7CADMMCMgwLyDAsIMOwgAzDAjIOkZbpGne+ZE0AAAAASUVORK"
            + "5CYII=";

    /** Le pied de page réellement enregistré par le laboratoire : trois lignes. */
    private static final String PIED_REEL =
            "Centre ADECHINA Anatomie Pathologique • Adresse : Carre 1915 \"G\" Fifadji, "
            + "072 BP 059 Cotonou, Bénin • Téléphone : (+229) 97761721 • WhatsApp: "
            + "(+229)61191975 • RCCM RB/COT/18 B22364 • IFU : 3201810410828 • contact@caap.bj "
            + "• Ouvert du Lundi au Vendredi de 08:00 - 17:00 • www.caap.bj";

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
        return rendre(nombreDeLignes, SIGNATURE_CARREE);
    }

    private byte[] rendre(int nombreDeLignes, String signature) throws Exception {
        Context ctx = contexteDeBase(nombreDeLignes, signature);
        return rendreLe(ctx);
    }

    private Context contexteDeBase(int nombreDeLignes, String signature) {
        StringBuilder corps = new StringBuilder();
        for (int i = 1; i <= nombreDeLignes; i++) {
            corps.append("<p>Ligne ").append(i)
                 .append(" du compte rendu, de longueur ordinaire pour un paragraphe.</p>");
        }

        Context ctx = new Context();
        ctx.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(CONTEXTE_SPRING, null));
        for (String vide : new String[] {
                "qrcode", "enteteImg", "contentMicro",
                "contentSupplementaire", "contentSupplementaireMicro" }) {
            ctx.setVariable(vide, "");
        }
        // Une image de signature carrée : `width="85"` sans hauteur laisse le
        // rapport d'aspect décider, et la signature réelle du laboratoire est
        // haute. C'est ce qui gonfle le bloc chez eux.
        ctx.setVariable("signature1Img", signature);
        ctx.setVariable("code", "26-0003");
        ctx.setVariable("testOrderCode", "26-0003");
        ctx.setVariable("title", "COMPTE RENDU");
        ctx.setVariable("content", corps.toString());
        ctx.setVariable("signatureDate", "05/08/2026");
        ctx.setVariable("prelevementDate", "01/08/2026");
        ctx.setVariable("createdAt", "02/08/2026");
        ctx.setVariable("currentDate", "05/08/2026");
        ctx.setVariable("testAffiliate", "Histologie");
        ctx.setVariable("patientFirstname", "PRENOMSDUPATIENT");
        ctx.setVariable("patientLastname", "NOMDUPATIENT");
        ctx.setVariable("patientAge", "40");
        ctx.setVariable("patientAgeUnit", "ans");
        ctx.setVariable("patientGenre", "F");
        ctx.setVariable("doctorName", "Dr Prescripteur");
        ctx.setVariable("hospitalName", "Service");
        ctx.setVariable("signator", SIGNATAIRE);
        ctx.setVariable("reviewedBy", "Dr Relecteur");
        ctx.setVariable("reportReviewTitle", "Signé électroniquement par :");
        ctx.setVariable("footer", PIED_REEL);
        ctx.setVariable("status", 1);

        return ctx;
    }

    private byte[] rendreLe(Context ctx) throws Exception {
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

    @Test
    @DisplayName("Sonde : ce que la marge basse réserve, et ce que le pied occupe")
    void sondeDuPiedDePage() throws Exception {
        List<Trace> lues = traces(rendre(10));
        double hautDuPied = lues.stream()
                .filter(t -> t.texte().contains("Centre ADECHINA") || t.texte().contains("97761721")
                        || t.texte().contains("Vendredi"))
                .mapToDouble(Trace::y).min().orElse(-1);
        double basDuPied = lues.stream()
                .filter(t -> t.texte().contains("Centre ADECHINA") || t.texte().contains("97761721")
                        || t.texte().contains("Vendredi"))
                .mapToDouble(Trace::y).max().orElse(-1);
        System.out.printf("%n  pied tracé de %.0f à %.0f pt (page haute de 842)%n", hautDuPied, basDuPied);
        System.out.printf("  marge basse réservée : %.0f pt%n", 115 * 0.75);
        System.out.printf("  bas du cadre de contenu : %.0f pt%n", BAS_DU_CADRE);
    }

    /**
     * Taille à laquelle chaque image est réellement tracée sur la page.
     *
     * <p>On laisse PDFBox interpréter le flux de contenu plutôt que d'y chercher
     * une matrice à la main : l'échelle appliquée à une image est le produit de
     * toutes les transformations en cours, et une lecture textuelle en manque
     * une dès que le moteur en empile deux.</p>
     */
    private List<double[]> imagesTracees(byte[] pdf) throws Exception {
        List<double[]> tailles = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(pdf)) {
            var moteur = new org.apache.pdfbox.contentstream.PDFStreamEngine() {
                @Override
                protected void processOperator(org.apache.pdfbox.contentstream.operator.Operator operateur,
                        List<org.apache.pdfbox.cos.COSBase> operandes) throws java.io.IOException {
                    if ("Do".equals(operateur.getName())) {
                        var matrice = getGraphicsState().getCurrentTransformationMatrix();
                        tailles.add(new double[] {
                                matrice.getScalingFactorX(), matrice.getScalingFactorY() });
                    }
                    super.processOperator(operateur, operandes);
                }
            };
            // Sans ces opérateurs, le moteur ne suit pas la matrice courante et
            // rend l'identité pour toute image : c'est « cm » qui porte l'échelle,
            // et « q »/« Q » qui la restaurent.
            moteur.addOperator(new org.apache.pdfbox.contentstream.operator.state.Concatenate());
            moteur.addOperator(new org.apache.pdfbox.contentstream.operator.state.Save());
            moteur.addOperator(new org.apache.pdfbox.contentstream.operator.state.Restore());
            moteur.addOperator(new org.apache.pdfbox.contentstream.operator.state.SetMatrix());
            for (var page : doc.getPages()) {
                moteur.processPage(page);
            }
        }
        return tailles;
    }

    @Test
    @DisplayName("La signature est plafonnée sans être déformée")
    void signatureTraceeSansDeformation() throws Exception {
        for (String forme : new String[] {SIGNATURE_CARREE, SIGNATURE_BANDEAU}) {
            verifierLaForme(forme);
        }
    }

    private void verifierLaForme(String signature) throws Exception {
        boolean carree = signature.equals(SIGNATURE_CARREE);
        double rapportAttendu = carree ? 1.0 : 400.0 / 120.0;
        List<double[]> images = imagesTracees(rendre(5, signature));
        assertThat(images).describedAs("aucune image tracée").isNotEmpty();

        for (double[] taille : images) {
            System.out.printf("  %s tracée : %.1f x %.1f pt (rapport %.2f, attendu %.2f)%n",
                    carree ? "carrée " : "bandeau", taille[0], taille[1],
                    taille[0] / taille[1], rapportAttendu);
            assertThat(taille[0] / taille[1])
                    .describedAs("signature déformée : %.1f x %.1f pt", taille[0], taille[1])
                    .isCloseTo(rapportAttendu, org.assertj.core.data.Offset.offset(0.05));
            assertThat(taille[1])
                    .describedAs("signature plus haute que l'espace réservé")
                    .isLessThanOrEqualTo(45 * 0.75 + 1);
            assertThat(taille[0])
                    .describedAs("signature plus large que l'espace réservé")
                    .isLessThanOrEqualTo(85 * 0.75 + 1);
        }
    }

    @Test
    @DisplayName("Sonde : le HTML rendu contient-il la balise de signature ?")
    void sondeDuHtml() throws Exception {
        Context ctx = contexteDeBase(5, SIGNATURE_CARREE);
        String html = com.labo.anapath.common.pdf.PdfHtmlUtil.toXhtml(moteur.process("pdf/rapport", ctx));
        int i = html.indexOf("<img");
        System.out.println("  balise : "
                + (i < 0 ? "AUCUNE" : html.substring(i, Math.min(i + 90, html.length()))));
        System.out.println("  longueur du HTML : " + html.length());
        System.out.println("  contient 'data:image' : " + html.contains("data:image"));
    }

    @Test
    @DisplayName("Sonde : que contient le PDF comme ressources d'image ?")
    void sondeDesRessources() throws Exception {
        byte[] pdf = rendre(5, SIGNATURE_CARREE);
        java.nio.file.Files.write(java.nio.file.Path.of("target/essai-signature.pdf"), pdf);
        try (PDDocument doc = PDDocument.load(pdf)) {
            int page = 0;
            for (var p : doc.getPages()) {
                page++;
                var res = p.getResources();
                for (var nom : res.getXObjectNames()) {
                    var xo = res.getXObject(nom);
                    System.out.printf("  page %d : XObject %s → %s%n", page, nom.getName(),
                            xo.getClass().getSimpleName());
                    if (xo instanceof org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject img) {
                        System.out.printf("      pixels %d x %d%n", img.getWidth(), img.getHeight());
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Sonde : le moteur dessine-t-il une image en data URI, hors gabarit ?")
    void sondeMinimale() throws Exception {
        String[] essais = {
            "<html><body><img width='85' src='" + SIGNATURE_CARREE + "'/></body></html>",
            "<html><body><img src='" + SIGNATURE_CARREE + "'/></body></html>",
        };
        for (String html : essais) {
            byte[] pdf;
            try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
                PdfRendererBuilder constructeur = new PdfRendererBuilder();
                constructeur.withHtmlContent(com.labo.anapath.common.pdf.PdfHtmlUtil.toXhtml(html), null);
                constructeur.toStream(sortie);
                constructeur.run();
                pdf = sortie.toByteArray();
            }
            String brut = new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1);
            System.out.printf("  %s → %d octets, /Image ×%d%n",
                    html.contains("width") ? "avec width" : "sans width",
                    pdf.length, brut.split("/Image", -1).length - 1);
        }
    }

    @Test
    @DisplayName("Le nom et les prénoms sont sous les bons libellés")
    void identiteSousLesBonsLibelles() throws Exception {
        List<Trace> lues = traces(rendre(5));

        // Les quatre cellules du tableau d'identité sont sur deux lignes ; on
        // apparie chaque libellé à la valeur qui lui succède sur la sienne.
        Trace libelleNom = lues.stream()
                .filter(t -> t.texte().startsWith("Nom")).findFirst().orElseThrow();
        Trace libellePrenoms = lues.stream()
                .filter(t -> t.texte().startsWith("Prénoms")).findFirst().orElseThrow();

        String surLigneDuNom = valeurSurLaLigne(lues, libelleNom.y());
        String surLigneDesPrenoms = valeurSurLaLigne(lues, libellePrenoms.y());

        // C'est le défaut signalé par le laboratoire : « Nom » portait le
        // prénom, et « Prénoms » le nom. Les formulaires de saisie, eux,
        // étiquettent `lastname` comme nom depuis toujours.
        assertThat(surLigneDuNom)
                .describedAs("la valeur écrite en face de « Nom : »")
                .isEqualTo("NOMDUPATIENT");
        assertThat(surLigneDesPrenoms)
                .describedAs("la valeur écrite en face de « Prénoms : »")
                .isEqualTo("PRENOMSDUPATIENT");
    }

    /** La valeur d'identité tracée sur la même ligne qu'un libellé donné. */
    private String valeurSurLaLigne(List<Trace> lues, double y) {
        return lues.stream()
                .filter(t -> Math.abs(t.y() - y) < 2)
                .map(Trace::texte)
                .filter(t -> t.equals("NOMDUPATIENT") || t.equals("PRENOMSDUPATIENT"))
                .findFirst()
                .orElse("(rien)");
    }
}

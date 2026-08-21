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
 * La mise en forme rédigée dans l'éditeur, retrouvée dans le PDF produit.
 *
 * <p>Le compte rendu doit sortir tel qu'il a été composé. Le gabarit ne portait
 * aucune règle pour le contenu riche : le moteur appliquait sa feuille par
 * défaut, et titres, listes et citations prenaient des dimensions qui n'étaient
 * celles de personne.</p>
 *
 * <p>Lire la feuille de style ne prouve rien — c'est le moteur qui tranche. On
 * rend donc le vrai gabarit et on relève la taille des glyphes tracés, ainsi
 * que la position horizontale du texte, seule façon de constater un retrait de
 * liste ou une citation.</p>
 */
class MiseEnFormeDuContenuTest {

    private final TemplateEngine moteur = creerMoteur();

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

    /** Un fragment tracé : son texte, sa taille de glyphe, son abscisse. */
    private record Trace(String texte, double taille, double x) {}

    private List<Trace> tracer(String contenu) throws Exception {
        byte[] pdf = rendre(contenu);
        List<Trace> traces = new ArrayList<>();
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDFTextStripper releveur = new PDFTextStripper() {
                @Override
                protected void writeString(String texte, List<TextPosition> positions) {
                    if (positions.isEmpty() || texte.isBlank()) return;
                    TextPosition p = positions.get(0);
                    traces.add(new Trace(texte.trim(), p.getFontSizeInPt(), p.getXDirAdj()));
                }
            };
            releveur.setSortByPosition(true);
            releveur.getText(doc);
        }
        return traces;
    }

    private Trace trouver(List<Trace> traces, String marqueur) {
        return traces.stream()
                .filter(t -> t.texte().contains(marqueur))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "« " + marqueur + " » n'est pas tracé. Relevé : " + traces));
    }

    @Test
    @DisplayName("les six niveaux de titre décroissent, et tous dépassent le texte courant")
    void lesTitresSontDistincts() throws Exception {
        List<Trace> t = tracer("""
                <h1>ZTITREUNZ</h1><h2>ZTITREDEUXZ</h2><h3>ZTITRETROISZ</h3>
                <h4>ZTITREQUATREZ</h4><h5>ZTITRECINQZ</h5><h6>ZTITRESIXZ</h6>
                <p>ZPARAGRAPHEZ</p>""");

        double corps = trouver(t, "ZPARAGRAPHEZ").taille();
        double[] titres = {
                trouver(t, "ZTITREUNZ").taille(), trouver(t, "ZTITREDEUXZ").taille(),
                trouver(t, "ZTITRETROISZ").taille(), trouver(t, "ZTITREQUATREZ").taille(),
                trouver(t, "ZTITRECINQZ").taille(), trouver(t, "ZTITRESIXZ").taille(),
        };

        // Le défaut d'origine : « Titre 1 » sortait de l'éditeur à la taille
        // d'un paragraphe, le reset de Tailwind ayant effacé les titres. Le PDF,
        // lui, appliquait les défauts du moteur — un troisième rendu encore.
        assertThat(titres[0]).as("h1 plus grand que le corps").isGreaterThan(corps);
        for (int i = 0; i < titres.length - 1; i++) {
            assertThat(titres[i])
                    .as("h%d au moins aussi grand que h%d".formatted(i + 1, i + 2))
                    .isGreaterThanOrEqualTo(titres[i + 1]);
        }
        // h1 = 1.6em sur 13px : 20,8 px, soit 15,6 pt.
        assertThat(titres[0]).isCloseTo(15.6, org.assertj.core.data.Offset.offset(0.6));
    }

    @Test
    @DisplayName("l'échelle des titres est la nôtre, pas celle du moteur")
    void lEchelleEstLaNotre() throws Exception {
        List<Trace> t = tracer("<p>ZPARAGRAPHEZ</p><h1>ZTITREUNZ</h1><h2>ZTITREDEUXZ</h2>");

        double corps = trouver(t, "ZPARAGRAPHEZ").taille();
        double h1 = trouver(t, "ZTITREUNZ").taille() / corps;
        double h2 = trouver(t, "ZTITREDEUXZ").taille() / corps;

        // Ce sont les deux seuls niveaux où notre échelle et celle du moteur
        // divergent assez pour être mesurées : le moteur pose h1 à 2em et h2 à
        // 1.5em, nous 1.6 et 1.32. Retirer nos règles ferait remonter les deux
        // au-dessus de leur borne — c'est ce que ce test surveille.
        //
        // Les tailles sont comparées largement : le moteur arrondit les corps
        // au point entier, et 13 px valent 9,75 pt. Exiger la deuxième décimale
        // rendrait le test faux pour une raison sans rapport avec la mise en
        // forme.
        assertThat(h1).as("h1 : 1.6em chez nous, 2em par défaut").isBetween(1.4, 1.8);
        assertThat(h2).as("h2 : 1.32em chez nous, 1.5em par défaut").isBetween(1.2, 1.42);
        assertThat(h1).as("h1 au-dessus de h2").isGreaterThan(h2);
    }

    @Test
    @DisplayName("une liste est retraitée, sans les 40 px du moteur")
    void laListeEstRetraitee() throws Exception {
        List<Trace> t = tracer("<p>ZPARAGRAPHEZ</p><ul><li>ZPUCEZ</li></ul>");

        double gauche = trouver(t, "ZPARAGRAPHEZ").x();
        double puce = trouver(t, "ZPUCEZ").x();
        double retrait = puce - gauche;

        // 1.7em sur 13 px = 22,1 px = 16,6 pt. Le défaut du moteur en pose 40,
        // soit 30 pt : presque le double, sur une feuille où chaque point de
        // marge compte.
        assertThat(retrait).as("la puce est en retrait").isGreaterThan(8.0);
        assertThat(retrait).as("mais pas des 30 pt du moteur").isLessThan(24.0);
    }

    @Test
    @DisplayName("une citation garde sa marge de page, seul son filet la décale")
    void laCitationNeSeDecalePas() throws Exception {
        List<Trace> t = tracer("<p>ZPARAGRAPHEZ</p><blockquote>ZCITATIONZ</blockquote>");

        double gauche = trouver(t, "ZPARAGRAPHEZ").x();
        double citation = trouver(t, "ZCITATIONZ").x();

        // Le moteur pose 40 px de chaque côté par défaut — 30 pt qui déplacent
        // le texte de façon très visible sur A4. Le filet gauche et son
        // remplissage suffisent à dire la citation : moins de 12 pt.
        assertThat(citation - gauche).isBetween(2.0, 12.0);
    }

    @Test
    @DisplayName("une ligne préformatée longue reste dans la feuille")
    void lePreformateNeDeborde() throws Exception {
        String longue = "Z".repeat(400);
        List<Trace> t = tracer("<pre>DEBUT" + longue + "FIN</pre>");

        // `white-space: pre` — le défaut du moteur — ne coupe jamais la ligne :
        // elle sortait de la page, et rien sur une feuille ne permet de faire
        // défiler. C'est une perte de contenu, pas un défaut d'esthétique.
        assertThat(t).as("le préformaté doit revenir à la ligne").hasSizeGreaterThan(1);
        assertThat(t.stream().anyMatch(x -> x.texte().contains("FIN")))
                .as("la fin de la ligne est tracée").isTrue();
        // A4 fait 595 pt de large ; la marge droite est à 30 px, soit 22,5 pt.
        assertThat(t.stream().mapToDouble(Trace::x).max().orElse(0))
                .as("rien ne sort du cadre").isLessThan(595.0 - 20.0);
    }

    private byte[] rendre(String contenu) throws Exception {
        Context ctx = new Context();
        ctx.setVariable(ThymeleafEvaluationContext.THYMELEAF_EVALUATION_CONTEXT_CONTEXT_VARIABLE_NAME,
                new ThymeleafEvaluationContext(CONTEXTE_SPRING, null));
        for (String vide : new String[] {
                "qrcode", "enteteImg", "contentMicro", "signature1Img",
                "contentSupplementaire", "contentSupplementaireMicro", "footer" }) {
            ctx.setVariable(vide, "");
        }
        ctx.setVariable("content", contenu);
        ctx.setVariable("code", "26-0003");
        ctx.setVariable("testOrderCode", "26-0003");
        ctx.setVariable("title", "COMPTE RENDU");
        ctx.setVariable("signatureDate", "05/08/2026");
        ctx.setVariable("prelevementDate", "01/08/2026");
        ctx.setVariable("createdAt", "02/08/2026");
        ctx.setVariable("currentDate", "05/08/2026");
        ctx.setVariable("testAffiliate", "Histologie");
        ctx.setVariable("patientFirstname", "Prenoms");
        ctx.setVariable("patientLastname", "NOM");
        ctx.setVariable("patientAge", "40");
        ctx.setVariable("patientAgeUnit", "ans");
        ctx.setVariable("patientGenre", "F");
        ctx.setVariable("doctorName", "Dr Prescripteur");
        ctx.setVariable("hospitalName", "Service");
        ctx.setVariable("signator", "Dr Signataire");
        ctx.setVariable("reviewedBy", "Dr Relecteur");
        ctx.setVariable("reportReviewTitle", "Signé électroniquement par :");
        ctx.setVariable("status", 1);

        String html = com.labo.anapath.common.pdf.PdfHtmlUtil.toXhtml(
                moteur.process("pdf/rapport", ctx));
        try (ByteArrayOutputStream sortie = new ByteArrayOutputStream()) {
            PdfRendererBuilder constructeur = new PdfRendererBuilder();
            com.labo.anapath.common.pdf.PdfFonts.enregistrerSubstituts(constructeur);
            constructeur.withHtmlContent(html, null);
            constructeur.toStream(sortie);
            constructeur.run();
            return sortie.toByteArray();
        }
    }
}

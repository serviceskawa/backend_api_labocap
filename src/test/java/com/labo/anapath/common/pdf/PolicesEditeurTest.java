package com.labo.anapath.common.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * Fidélité du PDF aux choix de mise en forme du rédacteur.
 *
 * <p>OpenHTMLToPDF ne reconnaît aucun nom commercial de police : « Arial » seul
 * lui est inconnu et le fait retomber sur du Times. L'éditeur, lui, produit des
 * balises {@code <font face="Arial">} héritées, que le moteur n'interprète pas
 * davantage. Le choix du médecin disparaissait donc deux fois.</p>
 *
 * <p>Ces tests ouvrent le PDF produit et lisent la police réellement embarquée :
 * c'est la seule vérification qui vaille, la substitution étant silencieuse.</p>
 */
class PolicesEditeurTest {

    private Set<String> policesDuPdf(String corpsHtml) {
        String html = "<html><body>" + corpsHtml + "</body></html>";
        byte[] pdf;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(PdfHtmlUtil.toXhtml(html), null);
            builder.toStream(out);
            builder.run();
            pdf = out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Set<String> trouvees = new LinkedHashSet<>();
        Matcher m = Pattern.compile("/BaseFont\\s*/([A-Za-z0-9+,#-]+)")
                .matcher(new String(pdf, StandardCharsets.ISO_8859_1));
        while (m.find()) trouvees.add(m.group(1));
        return trouvees;
    }

    @Test
    @DisplayName("Arial choisi dans l'éditeur donne bien de l'Helvetica, non du Times")
    void arialDonneHelvetica() {
        assertThat(policesDuPdf("<font face=\"Arial\">Texte</font>"))
                .contains("Helvetica")
                .doesNotContain("Times-Roman");
    }

    @Test
    @DisplayName("Times New Roman reste en serif")
    void timesResteSerif() {
        assertThat(policesDuPdf("<font face=\"Times New Roman\">Texte</font>"))
                .contains("Times-Roman");
    }

    @Test
    @DisplayName("Courier New donne une chasse fixe, non du Times")
    void courierDonneChasseFixe() {
        assertThat(policesDuPdf("<font face=\"Courier New\">Texte</font>"))
                .contains("Courier")
                .doesNotContain("Times-Roman");
    }

    /**
     * Georgia et Verdana n'ont pas d'équivalent parmi les quatorze polices de
     * base. À défaut de les embarquer, l'intention est respectée : l'une reste
     * une serif, l'autre une sans-serif — au lieu de devenir toutes deux du
     * Times.
     */
    @Test
    @DisplayName("Georgia reste serif, Verdana reste sans-serif")
    void georgiaEtVerdanaGardentLeurGenre() {
        assertThat(policesDuPdf("<font face=\"Georgia\">Texte</font>")).contains("Times-Roman");
        assertThat(policesDuPdf("<font face=\"Verdana\">Texte</font>")).contains("Helvetica");
    }

    @Test
    @DisplayName("L'italique appliqué sur une police choisie est conservé")
    void italiqueConserve() {
        assertThat(policesDuPdf("<font face=\"Arial\"><i>Texte</i></font>"))
                .contains("Helvetica-Oblique");
    }

    @Test
    @DisplayName("Le gras appliqué sur une police choisie est conservé")
    void grasConserve() {
        assertThat(policesDuPdf("<font face=\"Arial\"><b>Texte</b></font>"))
                .contains("Helvetica-Bold");
    }

    /**
     * {@code <font size>} n'est pas du CSS : le moteur l'ignore. La taille
     * choisie par le médecin était donc perdue, sans que rien ne le signale.
     */
    @Test
    @DisplayName("La taille choisie dans l'éditeur est reportée dans le PDF")
    void tailleReportee() {
        String xhtml = PdfHtmlUtil.toXhtml(
                "<html><body><font size=\"5\">Texte</font></body></html>");
        assertThat(xhtml).contains("font-size:24px");
    }

    @Test
    @DisplayName("Une font-family déjà générique n'est pas retouchée")
    void familleGeneriqueInchangee() {
        String xhtml = PdfHtmlUtil.toXhtml(
                "<html><body><span style=\"font-family: Arial, sans-serif;\">T</span></body></html>");
        assertThat(xhtml).contains("Arial, sans-serif").doesNotContain("sans-serif, sans-serif");
    }
}

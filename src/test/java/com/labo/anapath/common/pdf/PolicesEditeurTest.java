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
        return policesDuPdf(corpsHtml, true);
    }

    private Set<String> policesDuPdf(String corpsHtml, boolean avecSubstituts) {
        String html = "<html><body>" + corpsHtml + "</body></html>";
        byte[] pdf;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            if (avecSubstituts) {
                PdfFonts.enregistrerSubstituts(builder);
            }
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
     * Les cinq polices sans équivalent parmi les quatorze de base doivent
     * désormais être <b>embarquées</b>, et non approchées : c'est tout l'objet
     * des substituts libres. Le test lit le nom de la police effectivement
     * incluse dans le document.
     */
    @Test
    @DisplayName("Georgia s'incarne en Gelasio, embarquée dans le document")
    void georgiaEmbarquee() {
        assertThat(String.join(",", policesDuPdf("<font face=\"Georgia\">Texte</font>")))
                .contains("Gelasio");
    }

    @Test
    @DisplayName("Verdana, Tahoma et Trebuchet MS s'incarnent en DejaVu Sans")
    void sansSerifLargesEmbarquees() {
        for (String f : new String[] {"Verdana", "Tahoma", "Trebuchet MS"}) {
            assertThat(String.join(",", policesDuPdf("<font face=\"" + f + "\">Texte</font>")))
                    .as("police embarquée pour %s", f)
                    .contains("DejaVu");
        }
    }

    @Test
    @DisplayName("Comic Sans MS s'incarne en Comic Neue")
    void comicSansEmbarquee() {
        assertThat(String.join(",", policesDuPdf("<font face=\"Comic Sans MS\">Texte</font>")))
                .contains("ComicNeue");
    }

    /**
     * Le repli reste utile pour ce que l'éditeur ne propose pas : un
     * copier-coller depuis un traitement de texte peut introduire n'importe
     * quelle famille. L'intention est alors préservée — une serif reste une
     * serif — au lieu que tout devienne du Times.
     */
    @Test
    @DisplayName("Une police inconnue garde au moins son genre")
    void policeInconnueGardeSonGenre() {
        assertThat(policesDuPdf("<font face=\"Garamond\">Texte</font>", false))
                .contains("Times-Roman");
        assertThat(policesDuPdf("<font face=\"Calibri\">Texte</font>", false))
                .contains("Helvetica");
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
    @DisplayName("La taille reportée est celle que l'éditeur affiche, non celle du navigateur")
    void tailleReportee() {
        // L'éditeur intitule « 18 » la valeur 5 ; les tailles historiques du
        // navigateur y verraient 24 px, soit un tiers de trop.
        assertThat(PdfHtmlUtil.toXhtml("<html><body><font size=\"5\">T</font></body></html>"))
                .contains("font-size:18px");
        assertThat(PdfHtmlUtil.toXhtml("<html><body><font size=\"3\">T</font></body></html>"))
                .contains("font-size:12px");
    }

    @Test
    @DisplayName("Une font-family déjà générique n'est pas retouchée")
    void familleGeneriqueInchangee() {
        String xhtml = PdfHtmlUtil.toXhtml(
                "<html><body><span style=\"font-family: Arial, sans-serif;\">T</span></body></html>");
        assertThat(xhtml).contains("Arial, sans-serif").doesNotContain("sans-serif, sans-serif");
    }
}

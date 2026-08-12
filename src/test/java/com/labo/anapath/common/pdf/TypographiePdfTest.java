package com.labo.anapath.common.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.Inflater;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * Garde-fou typographique : vérifie ce qui est <b>réellement tracé</b> dans le
 * PDF, et non ce que la feuille de style déclare.
 *
 * <p>OpenHTMLToPDF ignore en silence toute famille qu'on ne lui a pas
 * enregistrée : le document sort en Helvetica sans le moindre avertissement.
 * C'est ainsi que l'état de caisse et la fiche de paie ont dérivé sans que
 * personne s'en aperçoive. Un test qui lirait la feuille de style ne verrait
 * rien ; celui-ci ouvre le fichier produit.</p>
 */
class TypographiePdfTest {

    /** Corps réellement tracés, lus dans les flux de contenu du PDF. */
    private List<Double> corpsTraces(byte[] pdf) {
        List<Double> tailles = new ArrayList<>();
        Matcher flux = Pattern.compile("stream\\r?\\n", Pattern.MULTILINE)
                .matcher(new String(pdf, StandardCharsets.ISO_8859_1));
        String brut = new String(pdf, StandardCharsets.ISO_8859_1);
        int depuis = 0;
        while (flux.find(depuis)) {
            int debut = flux.end();
            int fin = brut.indexOf("endstream", debut);
            if (fin < 0) break;
            byte[] comprime = brut.substring(debut, fin).getBytes(StandardCharsets.ISO_8859_1);
            String contenu = decompresser(comprime);
            Matcher tf = Pattern.compile("/[A-Za-z0-9]+\\s+([0-9.]+)\\s+Tf").matcher(contenu);
            while (tf.find()) {
                tailles.add(Double.parseDouble(tf.group(1)));
            }
            depuis = fin;
        }
        return tailles;
    }

    private String decompresser(byte[] donnees) {
        Inflater inflater = new Inflater();
        inflater.setInput(donnees);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] tampon = new byte[4096];
            while (!inflater.finished()) {
                int n = inflater.inflate(tampon);
                if (n == 0) break;
                out.write(tampon, 0, n);
            }
            return out.toString(StandardCharsets.ISO_8859_1);
        } catch (Exception e) {
            return "";
        } finally {
            inflater.end();
        }
    }

    private byte[] rendre(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(PdfHtmlUtil.toXhtml(html), null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Le compte rendu du legacy sortait en Arial 13 px sous DomPDF, réglé à
     * 96 DPI (config/dompdf.php) — soit 9,75 pt. La même conversion doit valoir
     * ici, sans quoi tout le document change d'échelle.
     */
    @Test
    @DisplayName("13 px se traduisent par 9,75 pt, comme DomPDF à 96 DPI")
    void conversionPixelsVersPoints() {
        byte[] pdf = rendre("""
                <html><head><style>
                  body { font-family: Arial, sans-serif; font-size: 13px; }
                </style></head><body><p>Aspect macroscopique</p></body></html>
                """);

        assertThat(corpsTraces(pdf)).isNotEmpty().contains(9.75);
    }

    @Test
    @DisplayName("Les tailles du compte rendu : corps 9,75 pt, titres 12 pt, pied 9 pt")
    void echelleDuCompteRendu() {
        byte[] pdf = rendre("""
                <html><head><style>
                  body { font-family: Arial, sans-serif; font-size: 13px; }
                  .titre { font-size: 16px; }
                  .pied  { font-size: 12px; }
                </style></head><body>
                  <p>Corps</p><p class="titre">Titre</p><p class="pied">Pied</p>
                </body></html>
                """);

        assertThat(corpsTraces(pdf)).contains(9.75, 12.0, 9.0);
    }

    /**
     * Sans enregistrement, une famille non standard est remplacée par Helvetica.
     * Ce test fige le constat : il documente le piège plutôt que de le subir, et
     * échouerait si une version d'OpenHTMLToPDF changeait ce comportement.
     */
    @Test
    @DisplayName("Une police non enregistrée retombe sur les polices de base")
    void policeNonEnregistreeRetombeSurHelvetica() {
        byte[] pdf = rendre("""
                <html><head><style>
                  body { font-family: "Nunito", sans-serif; font-size: 13px; }
                </style></head><body><p>Facture</p></body></html>
                """);

        assertThat(new String(pdf, StandardCharsets.ISO_8859_1))
                .contains("/Helvetica")
                .doesNotContain("Nunito");
    }

    @Test
    @DisplayName("Une police enregistrée est bien embarquée dans le document")
    void policeEnregistreeEstEmbarquee() {
        byte[] pdf;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            PdfFonts.enregistrerNunito(builder);
            builder.withHtmlContent(PdfHtmlUtil.toXhtml("""
                    <html><head><style>
                      body { font-family: "Nunito", sans-serif; font-size: 13px; }
                    </style></head><body><p>Facture</p></body></html>
                    """), null);
            builder.toStream(out);
            builder.run();
            pdf = out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).contains("Nunito");
    }
}

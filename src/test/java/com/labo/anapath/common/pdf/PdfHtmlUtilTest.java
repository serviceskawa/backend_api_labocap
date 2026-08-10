package com.labo.anapath.common.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * Vérifie que la normalisation produit un document qu'OpenHTMLToPDF accepte.
 *
 * <p>Les cas couverts viennent d'un incident réel : un compte rendu rédigé par
 * copier-coller depuis Word refusait de s'imprimer, l'analyseur s'arrêtant sur
 * <i>The prefix "o" for element "o:p" is not bound</i>.</p>
 */
class PdfHtmlUtilTest {

    /**
     * Le vrai juge n'est pas la chaîne produite mais le moteur de rendu : un test
     * qui se contenterait de chercher « o:p » passerait encore si la sortie
     * restait invalide pour une autre raison.
     */
    private void rendPdf(String html) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(PdfHtmlUtil.toXhtml(html), null);
            builder.toStream(out);
            builder.run();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("Le collage depuis Word n'empêche plus le rendu")
    void collageWord() {
        String html = "<html><body><p>Conclusion<o:p></o:p></p></body></html>";
        assertThatCode(() -> rendPdf(html)).doesNotThrowAnyException();
        assertThat(PdfHtmlUtil.toXhtml(html)).doesNotContain("o:p");
    }

    @Test
    @DisplayName("Le texte porté par une balise préfixée est conservé")
    void texteConserve() {
        // `unwrap` plutôt que suppression : suppression ferait disparaître ce mot
        // du compte rendu sans que personne ne s'en aperçoive.
        String xhtml = PdfHtmlUtil.toXhtml(
                "<p>Prélèvement <st1:place>Cotonou</st1:place> reçu</p>");
        assertThat(xhtml).contains("Cotonou").doesNotContain("st1:");
    }

    @Test
    @DisplayName("Les attributs préfixés partent, sauf xml:")
    void attributsPrefixes() {
        String xhtml = PdfHtmlUtil.toXhtml(
                "<p v:shapes=\"forme\" xml:lang=\"fr\">Texte</p>");
        assertThat(xhtml).doesNotContain("v:shapes").contains("xml:lang");
    }

    @Test
    @DisplayName("Un contenu ordinaire traverse la normalisation intact")
    void contenuOrdinaire() {
        String xhtml = PdfHtmlUtil.toXhtml("<p>Aspect <b>normal</b><br>Fin</p>");
        assertThat(xhtml).contains("Aspect").contains("<b>normal</b>").contains("Fin");
        assertThatCode(() -> rendPdf("<p>Aspect <b>normal</b><br>Fin</p>"))
                .doesNotThrowAnyException();
    }
}

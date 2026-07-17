package com.labo.anapath.common.pdf;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;

/**
 * Utilitaire de normalisation HTML → XHTML pour la génération PDF.
 *
 * <p>OpenHTMLToPDF exige du <b>XHTML strict et bien formé</b> : balises vides
 * fermées ({@code <br/>}, {@code <img/>}, {@code <hr/>}), entités échappées, etc.
 * Or les templates injectent du HTML issu de l'éditeur de texte enrichi
 * (contenu des comptes rendus) qui contient souvent des {@code <br>} non fermés,
 * des {@code &nbsp;} et des balises mal imbriquées — ce qui fait échouer le rendu.
 *
 * <p>On passe donc le HTML final par Jsoup (parseur HTML tolérant) et on le
 * ré-émet en syntaxe XML, produisant un document valide pour OpenHTMLToPDF.
 */
public final class PdfHtmlUtil {

    private PdfHtmlUtil() {}

    /**
     * Normalise un fragment/document HTML en XHTML bien formé.
     *
     * @param html HTML potentiellement mal formé (rendu Thymeleaf + contenu éditeur)
     * @return XHTML valide prêt pour {@code PdfRendererBuilder.withHtmlContent}
     */
    public static String toXhtml(String html) {
        if (html == null || html.isBlank()) {
            return "<html><body></body></html>";
        }
        Document doc = Jsoup.parse(html);
        doc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .charset("UTF-8")
                .prettyPrint(false);
        return doc.html();
    }
}

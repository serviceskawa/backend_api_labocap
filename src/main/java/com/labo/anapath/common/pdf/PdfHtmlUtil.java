package com.labo.anapath.common.pdf;

import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
        retirerPrefixesDeNamespace(doc);
        doc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .charset("UTF-8")
                .prettyPrint(false);
        return doc.html();
    }

    /**
     * Supprime les préfixes de namespace laissés par les traitements de texte.
     *
     * <p>Un copier-coller depuis Word insère des balises propres à Office —
     * {@code <o:p>}, {@code <w:sdt>}, {@code <v:shape>}, {@code <st1:place>} —
     * dont le préfixe n'est déclaré nulle part une fois le fragment recollé dans
     * l'éditeur. Jsoup les conserve fidèlement et l'analyseur XML d'OpenHTMLToPDF
     * s'arrête dessus : <i>The prefix "o" for element "o:p" is not bound</i>, et
     * le compte rendu devient impossible à imprimer.</p>
     *
     * <p>Les éléments sont <b>déballés</b> et non supprimés : {@code o:p} est
     * généralement vide, mais {@code st1:place} entoure du texte réel qu'une
     * suppression ferait disparaître du compte rendu sans avertissement.</p>
     *
     * <p>Les attributs préfixés partent aussi, à l'exception de {@code xml:},
     * seul préfixe lié d'office ({@code xml:lang} reste donc valide).</p>
     */
    private static void retirerPrefixesDeNamespace(Document doc) {
        // La liste est figée avant modification : `unwrap` remanie l'arbre, et
        // itérer directement sur la sélection sauterait des éléments.
        List<Element> prefixes = new ArrayList<>();
        for (Element el : doc.getAllElements()) {
            if (el.tagName().indexOf(':') >= 0) {
                prefixes.add(el);
            }
            List<String> aRetirer = new ArrayList<>();
            for (Attribute attr : el.attributes()) {
                String cle = attr.getKey();
                if (cle.indexOf(':') >= 0 && !cle.startsWith("xml:")) {
                    aRetirer.add(cle);
                }
            }
            aRetirer.forEach(el::removeAttr);
        }
        prefixes.forEach(Element::unwrap);
    }
}

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
        traduireBalisesFont(doc);
        ajouterFamillesGeneriques(doc);
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

    // ------------------------------------------------------------------
    // Fidélité aux choix de mise en forme du rédacteur
    // ------------------------------------------------------------------

    /**
     * Famille générique correspondant à une police nommée.
     *
     * <p>OpenHTMLToPDF ne connaît que les quatorze polices de base du PDF, et il
     * ne reconnaît <b>aucun</b> nom commercial : ni « Arial », ni « Courier
     * New ». Seul un mot-clé générique ({@code sans-serif}, {@code serif},
     * {@code monospace}) le fait aboutir. Sans lui il retombe sur son défaut —
     * du Times — quel que soit le choix du rédacteur.</p>
     */
    private static final java.util.Map<String, String> GENERIQUES = java.util.Map.ofEntries(
            java.util.Map.entry("arial", "sans-serif"),
            java.util.Map.entry("helvetica", "sans-serif"),
            java.util.Map.entry("verdana", "sans-serif"),
            java.util.Map.entry("tahoma", "sans-serif"),
            java.util.Map.entry("trebuchet ms", "sans-serif"),
            java.util.Map.entry("calibri", "sans-serif"),
            java.util.Map.entry("times new roman", "serif"),
            java.util.Map.entry("times", "serif"),
            java.util.Map.entry("georgia", "serif"),
            java.util.Map.entry("garamond", "serif"),
            java.util.Map.entry("book antiqua", "serif"),
            java.util.Map.entry("courier new", "monospace"),
            java.util.Map.entry("courier", "monospace"),
            java.util.Map.entry("consolas", "monospace"));

    /** Correspondance des tailles HTML historiques {@code <font size="1..7">}. */
    private static final java.util.Map<String, String> TAILLES_FONT = java.util.Map.of(
            "1", "10px", "2", "13px", "3", "16px", "4", "18px",
            "5", "24px", "6", "32px", "7", "48px");

    /**
     * Traduit les {@code <font face=… size=…>} en styles CSS.
     *
     * <p>L'éditeur de comptes rendus s'appuie sur {@code document.execCommand},
     * qui produit encore ces balises héritées faute de {@code styleWithCSS}.
     * Le moteur PDF ne les interprète pas : la police et la taille choisies par
     * le médecin disparaissaient purement et simplement du document.</p>
     *
     * <p>La balise est conservée — seulement enrichie d'un {@code style} — pour
     * ne pas perturber l'imbrication du contenu existant.</p>
     */
    private static void traduireBalisesFont(Document doc) {
        for (Element el : doc.select("font")) {
            StringBuilder style = new StringBuilder(el.attr("style"));
            String face = el.attr("face");
            if (!face.isBlank()) {
                ajouter(style, "font-family", face);
                el.removeAttr("face");
            }
            String size = el.attr("size");
            if (TAILLES_FONT.containsKey(size.trim())) {
                ajouter(style, "font-size", TAILLES_FONT.get(size.trim()));
                el.removeAttr("size");
            }
            String couleur = el.attr("color");
            if (!couleur.isBlank()) {
                ajouter(style, "color", couleur);
                el.removeAttr("color");
            }
            if (style.length() > 0) {
                el.attr("style", style.toString());
            }
        }
    }

    /**
     * Complète toute {@code font-family} dépourvue de famille générique.
     *
     * <p>Sans cet ajout, « Arial » seul donne du Times — vérifié en rendant le
     * document et en lisant la police effectivement embarquée. Avec
     * « Arial, sans-serif », on obtient Helvetica, métriquement équivalente.
     * Georgia et Verdana n'ont pas d'équivalent parmi les polices de base :
     * elles aboutissent respectivement à du serif et du sans-serif, ce qui
     * respecte au moins l'intention du rédacteur.</p>
     *
     * <p>Le repli par défaut est {@code sans-serif} : le corps du compte rendu
     * est en Arial, une police inconnue y détonnerait moins en sans qu'en
     * serif.</p>
     */
    private static void ajouterFamillesGeneriques(Document doc) {
        for (Element el : doc.select("[style]")) {
            String style = el.attr("style");
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("font-family\\s*:\\s*([^;]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(style);
            if (!m.find()) {
                continue;
            }
            String familles = m.group(1).trim();
            if (familles.toLowerCase().matches(".*\\b(sans-serif|serif|monospace|cursive|fantasy)\\b.*")) {
                continue;
            }
            String premiere = familles.split(",")[0].trim()
                    .replaceAll("^[\"']|[\"']$", "").toLowerCase();
            String generique = GENERIQUES.getOrDefault(premiere, "sans-serif");
            el.attr("style", style.substring(0, m.end()) + ", " + generique + style.substring(m.end()));
        }
    }

    private static void ajouter(StringBuilder style, String propriete, String valeur) {
        if (style.length() > 0 && style.charAt(style.length() - 1) != ';') {
            style.append(';');
        }
        style.append(propriete).append(':').append(valeur).append(';');
    }
}

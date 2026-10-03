package com.labo.anapath.common.security;

import com.labo.anapath.common.exception.BusinessException;
import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

import java.util.regex.Pattern;

/**
 * Nettoie une signature manuscrite avant de l'enregistrer (lot 3, côté API).
 *
 * <p>Le front la réinjecte dans la page comme HTML ; une signature qui
 * contiendrait un script serait exécutée chez chaque personne qui l'ouvre. Le
 * front nettoie aussi, mais la défense ne doit pas reposer sur lui seul : un
 * autre client, ou une autre version, afficherait la valeur telle quelle.</p>
 *
 * <p>Deux formes acceptées : une image en data-URL base64 (PNG, JPEG, WebP) ou
 * un SVG réduit à ses formes. Tout le reste est refusé.</p>
 */
public final class SignatureNettoyee {

    private static final Pattern IMAGE_BASE64 = Pattern.compile(
            "^data:image/(png|jpeg|jpg|webp);base64,[A-Za-z0-9+/=\\s]+$");
    /** La signature de profil est un nom de fichier d'image (voir PdfAssets.signature). */
    private static final Pattern NOM_DE_FICHIER = Pattern.compile("^[\\w.-]+\\.(png|jpe?g|webp)$");

    /** Balises et attributs d'un tracé : aucun lien, aucun script, aucun objet étranger. */
    private static final Safelist SVG = new Safelist()
            .addTags("svg", "g", "path", "line", "polyline", "polygon", "rect", "circle", "ellipse")
            .addAttributes(":all", "d", "viewBox", "fill", "stroke", "stroke-width", "stroke-linecap",
                    "stroke-linejoin", "fill-rule", "width", "height", "xmlns", "x", "y", "cx", "cy",
                    "r", "rx", "ry", "x1", "y1", "x2", "y2", "points", "transform", "opacity");

    private SignatureNettoyee() {}

    /**
     * @return la signature telle quelle (image) ou réduite à ses formes (SVG), ou nul si absente
     * @throws BusinessException si la valeur n'est ni une image base64 ni un SVG
     */
    public static String nettoyer(String signature) {
        if (signature == null || signature.isBlank()) return null;
        String s = signature.trim();
        if (IMAGE_BASE64.matcher(s).matches() || NOM_DE_FICHIER.matcher(s).matches()) return s;
        if (s.startsWith("<svg") || s.startsWith("<?xml")) {
            var doc = Jsoup.parse(s, "", Parser.xmlParser());
            var svg = doc.selectFirst("svg");
            if (svg == null) throw new BusinessException("Signature illisible.");
            String propre = Jsoup.clean(svg.outerHtml(), "", SVG, new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false));
            if (!propre.contains("<svg")) throw new BusinessException("Signature illisible.");
            return propre;
        }
        throw new BusinessException("Format de signature non pris en charge (image base64 ou SVG attendu).");
    }
}

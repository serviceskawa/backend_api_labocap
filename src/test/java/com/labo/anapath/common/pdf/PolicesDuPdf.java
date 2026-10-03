package com.labo.anapath.common.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Les polices d'un PDF, lues par PDFBox.
 *
 * <p>Les tests cherchaient {@code /BaseFont} dans les octets du fichier ;
 * PDFBox 3 range les dictionnaires dans des flux d'objets compressés, où une
 * recherche textuelle ne voit plus rien. Le préfixe de sous-ensemble
 * ({@code ABCDEF+}) est retiré pour que les noms restent comparables.</p>
 */
final class PolicesDuPdf {
    private PolicesDuPdf() {}

    static Set<String> noms(byte[] pdf) {
        Set<String> noms = new LinkedHashSet<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (PDPage page : doc.getPages()) {
                PDResources ressources = page.getResources();
                if (ressources == null) continue;
                for (COSName n : ressources.getFontNames()) {
                    PDFont police = ressources.getFont(n);
                    if (police == null || police.getName() == null) continue;
                    String nom = police.getName();
                    noms.add(nom.length() > 7 && nom.charAt(6) == '+' ? nom.substring(7) : nom);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return noms;
    }
}

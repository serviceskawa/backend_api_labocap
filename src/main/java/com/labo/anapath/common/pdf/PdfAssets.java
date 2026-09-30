package com.labo.anapath.common.pdf;

import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import com.labo.anapath.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.Base64;

/**
 * Éléments communs aux comptes-rendus imprimés : entête du laboratoire, pied de
 * page, titre de relecture et image de signature.
 *
 * <p>Extraits du rendu d'anatomie pathologique pour être partagés avec celui de
 * biologie, <b>sans rien en changer</b> : mêmes clés {@code setting_apps}, mêmes
 * replis, mêmes images embarquées. Deux documents d'un même laboratoire doivent
 * porter la même entête et le même pied de page.</p>
 *
 * <p>Utilitaire statique, comme {@link PdfFonts} et {@link PdfHtmlUtil} : le
 * dépôt des réglages est passé par l'appelant, ce qui laisse intacts les
 * constructeurs — et les tests — des services existants.</p>
 */
public final class PdfAssets {

    private static final Logger log = LoggerFactory.getLogger(PdfAssets.class);

    /** Entête livrée, utilisée tant qu'aucune n'a été téléversée. */
    public static final String ENTETE_PAR_DEFAUT = "pdf-assets/entete_pdf_cr.png";

    /** Dossier des signatures manuscrites des médecins. */
    public static final String DOSSIER_SIGNATURES = "pdf-assets/signatures/";

    /** Titre de relecture par défaut (réplique du rendu de référence CAAP). */
    public static final String TITRE_DE_RELECTURE_PAR_DEFAUT = "Signé électroniquement par :";

    private PdfAssets() {}

    /**
     * Entête image du laboratoire : priorité au réglage {@code entete} téléversé
     * (data URI base64, comme entete_pdf_cr.png dans Laravel) ; repli sur
     * l'image embarquée dans les ressources si aucune n'a été téléversée.
     */
    public static String entete(SettingAppRepository reglages) {
        String enteteSetting = reglages.findByKey("entete")
                .map(SettingApp::getValue).orElse("");
        return (enteteSetting != null && enteteSetting.startsWith("data:"))
                ? enteteSetting
                : imageEnDataUri(ENTETE_PAR_DEFAUT);
    }

    /** Pied de page ({@code report_footer}), ou le texte livré s'il est absent ou vide. */
    public static String piedDePage(SettingAppRepository reglages) {
        return reglages.findByKey("report_footer")
                .map(SettingApp::getValue).filter(v -> !v.isBlank())
                .orElse(SettingApp.DEFAULT_REPORT_FOOTER);
    }

    /**
     * Titre de relecture : « Signé électroniquement par : » par défaut, surchargé
     * par le réglage {@code report_review_title} s'il est défini.
     */
    public static String titreDeRelecture(SettingAppRepository reglages) {
        return reglages.findByKey("report_review_title")
                .map(SettingApp::getValue).filter(v -> !v.isBlank())
                .orElse(TITRE_DE_RELECTURE_PAR_DEFAUT);
    }

    /**
     * Image de la signature manuscrite d'un signataire, embarquée si le fichier
     * est disponible ; chaîne vide sinon.
     */
    public static String signature(User signataire) {
        if (signataire == null || signataire.getSignature() == null || signataire.getSignature().isBlank()) {
            return "";
        }
        return imageEnDataUri(DOSSIER_SIGNATURES + signataire.getSignature());
    }

    /**
     * Charge une image depuis le classpath et la renvoie en data URI base64
     * (embarquée dans le HTML, pour qu'OpenHTMLToPDF la rende sans baseUri).
     * Renvoie une chaîne vide si l'image est absente.
     */
    public static String imageEnDataUri(String classpathLocation) {
        try {
            ClassPathResource resource = new ClassPathResource(classpathLocation);
            if (!resource.exists()) {
                return "";
            }
            byte[] bytes;
            try (InputStream in = resource.getInputStream()) {
                bytes = in.readAllBytes();
            }
            String mime = classpathLocation.toLowerCase().endsWith(".jpg")
                    || classpathLocation.toLowerCase().endsWith(".jpeg")
                    ? "image/jpeg" : "image/png";
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("Chargement image PDF échoué ({}): {}", classpathLocation, e.getMessage());
            return "";
        }
    }
}

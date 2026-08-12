package com.labo.anapath.common.pdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;


import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * Enregistrement des polices auprès d'OpenHTMLToPDF.
 *
 * <p>Le moteur ne connaît que les quatorze polices de base du format PDF.
 * Toute autre famille déclarée dans une feuille de style est <b>ignorée en
 * silence</b> : le document sort en Helvetica, avec d'autres métriques et
 * d'autres retours à la ligne. Rien ne le signale, ni à la compilation ni à
 * l'exécution — seule une lecture du PDF produit révèle la substitution.</p>
 *
 * <p>C'est ce qui était arrivé à deux des quatre documents : la fermeture de
 * caisse et la fiche de paie n'enregistraient aucune police, quand le legacy
 * les imprimait en Nunito via le thème d'administration.</p>
 *
 * <p>Mutualisé ici précisément pour que l'oubli ne puisse pas se reproduire à
 * chaque nouveau document.</p>
 */
public final class PdfFonts {

    private static final Logger log = LoggerFactory.getLogger(PdfFonts.class);

    /** Famille des documents repris des vues imprimées par le navigateur. */
    public static final String NUNITO = "Nunito";

    private PdfFonts() {}

    /**
     * Enregistre les trois graisses de Nunito utilisées par les gabarits.
     *
     * <p>Police du thème d'administration Laravel ({@code app.min.css} :
     * {@code font-family: Nunito, sans-serif}), donc celle sous laquelle les
     * factures et les états de caisse sortaient de l'imprimante.</p>
     */
    public static void enregistrerNunito(PdfRendererBuilder builder) {
        enregistrer(builder, "pdf-assets/fonts/Nunito-Regular.ttf", NUNITO, 400);
        enregistrer(builder, "pdf-assets/fonts/Nunito-SemiBold.ttf", NUNITO, 600);
        enregistrer(builder, "pdf-assets/fonts/Nunito-Bold.ttf", NUNITO, 700);
    }

    /**
     * Enregistre une police, ou trace un avertissement si le fichier manque.
     *
     * <p>L'absence n'interrompt pas la génération : un document rendu dans une
     * police de substitution vaut mieux qu'un document non rendu. Mais elle est
     * journalisée, faute de quoi la substitution resterait invisible.</p>
     */
    public static void enregistrer(PdfRendererBuilder builder, String cheminClasspath,
                                   String famille, int graisse) {
        ClassPathResource ressource = new ClassPathResource(cheminClasspath);
        if (!ressource.exists()) {
            log.warn("Police PDF absente ({}) : rendu en police de substitution.", cheminClasspath);
            return;
        }
        builder.useFont(() -> {
            try {
                return ressource.getInputStream();
            } catch (Exception e) {
                throw new IllegalStateException("Lecture de la police " + cheminClasspath + " impossible", e);
            }
        }, famille, graisse, BaseRendererBuilder.FontStyle.NORMAL, true);
    }
}

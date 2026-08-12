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
     * Substituts libres des polices que le rédacteur peut choisir.
     *
     * <p>Le format PDF ne garantit que quatorze polices de base. Arial, Times
     * New Roman et Courier New y ont un équivalent aux métriques identiques —
     * Helvetica, Times, Courier — et n'ont donc rien à embarquer. Les autres
     * n'en ont aucun : elles sortaient toutes en Times.</p>
     *
     * <p>Les polices d'origine appartiennent à Microsoft et ne sont pas
     * redistribuables. Sont donc embarqués des substituts libres :</p>
     * <ul>
     *   <li><b>Georgia</b> → Gelasio, dessinée pour en reprendre les métriques
     *       (SIL OFL). Police variable : PDFBox en rend l'instance par défaut,
     *       le gras et l'italique sont donc synthétisés.</li>
     *   <li><b>Verdana, Tahoma, Trebuchet MS</b> → DejaVu Sans, la sans-serif
     *       libre la plus proche de cette largeur (licence Bitstream Vera,
     *       permissive). Les métriques diffèrent : le texte occupera une
     *       largeur un peu différente de l'original.</li>
     *   <li><b>Comic Sans MS</b> → Comic Neue (SIL OFL), son alternative libre
     *       reconnue.</li>
     * </ul>
     *
     * <p>Ces substituts <b>ressemblent</b> sans être identiques. Seule une
     * licence de redistribution des polices d'origine donnerait le dessin
     * exact ; c'est un arbitrage assumé, documenté ici pour qu'il ne se perde
     * pas.</p>
     */
    public static void enregistrerSubstituts(PdfRendererBuilder builder) {
        // Georgia : une seule fonte variable, déclinée sur les quatre styles.
        for (int graisse : new int[] {400, 700}) {
            for (BaseRendererBuilder.FontStyle style : new BaseRendererBuilder.FontStyle[] {
                    BaseRendererBuilder.FontStyle.NORMAL, BaseRendererBuilder.FontStyle.ITALIC}) {
                enregistrer(builder, "pdf-assets/fonts/Gelasio[wght].ttf", "Georgia", graisse, style);
            }
        }

        for (String famille : new String[] {"Verdana", "Tahoma", "Trebuchet MS"}) {
            enregistrer(builder, "pdf-assets/fonts/DejaVuSans.ttf", famille, 400, BaseRendererBuilder.FontStyle.NORMAL);
            enregistrer(builder, "pdf-assets/fonts/DejaVuSans-Bold.ttf", famille, 700, BaseRendererBuilder.FontStyle.NORMAL);
            enregistrer(builder, "pdf-assets/fonts/DejaVuSans-Oblique.ttf", famille, 400, BaseRendererBuilder.FontStyle.ITALIC);
            enregistrer(builder, "pdf-assets/fonts/DejaVuSans-BoldOblique.ttf", famille, 700, BaseRendererBuilder.FontStyle.ITALIC);
        }

        enregistrer(builder, "pdf-assets/fonts/ComicNeue-Regular.ttf", "Comic Sans MS", 400, BaseRendererBuilder.FontStyle.NORMAL);
        enregistrer(builder, "pdf-assets/fonts/ComicNeue-Bold.ttf", "Comic Sans MS", 700, BaseRendererBuilder.FontStyle.NORMAL);
        enregistrer(builder, "pdf-assets/fonts/ComicNeue-Italic.ttf", "Comic Sans MS", 400, BaseRendererBuilder.FontStyle.ITALIC);
        enregistrer(builder, "pdf-assets/fonts/ComicNeue-BoldItalic.ttf", "Comic Sans MS", 700, BaseRendererBuilder.FontStyle.ITALIC);
    }

    /** Variante droite, conservée pour les appels existants. */
    public static void enregistrer(PdfRendererBuilder builder, String cheminClasspath,
                                   String famille, int graisse) {
        enregistrer(builder, cheminClasspath, famille, graisse, BaseRendererBuilder.FontStyle.NORMAL);
    }

    /**
     * Enregistre une police, ou trace un avertissement si le fichier manque.
     *
     * <p>L'absence n'interrompt pas la génération : un document rendu dans une
     * police de substitution vaut mieux qu'un document non rendu. Mais elle est
     * journalisée, faute de quoi la substitution resterait invisible.</p>
     */
    public static void enregistrer(PdfRendererBuilder builder, String cheminClasspath,
                                   String famille, int graisse,
                                   BaseRendererBuilder.FontStyle style) {
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
        }, famille, graisse, style, true);
    }
}

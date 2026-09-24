package com.labo.anapath.biology.report;

import com.labo.anapath.biology.BiologyKind;
import com.labo.anapath.biology.results.BiologyAnalysisStatus;
import com.labo.anapath.biology.results.BiologyFlag;
import com.labo.anapath.biology.results.BiologyNumbers;
import com.labo.anapath.biology.results.BiologyWorksheetDto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ce que le compte-rendu de biologie imprime, construit à partir de la feuille de
 * saisie (B5).
 *
 * <h2>Règles</h2>
 * <ul>
 *   <li>Analyses regroupées par catégorie ({@code category_tests.name}), dans
 *       l'ordre de leur première apparition sur le bon ; sans catégorie :
 *       « {@value #SANS_CATEGORIE} ».</li>
 *   <li>Unité et valeurs de référence : les <b>copies faites à la saisie</b>
 *       ({@code *_snapshot}), jamais le catalogue du jour — un catalogue modifié
 *       ne réécrit pas un résultat rendu.</li>
 *   <li>Valeur : telle qu'enregistrée (arrondie à la saisie), virgule décimale.</li>
 *   <li>Hors normes ({@code L}, {@code H}, {@code LL}, {@code HH}, {@code A}) : en
 *       gras, suivi du libellé du laboratoire ({@code bio_flag_labels}) ; pas de
 *       flèche, que les polices du document ne garantissent pas.</li>
 *   <li>Paramètre {@code printable = false} : omis. Paramètre sans valeur : omis.
 *       Paramètre retiré du catalogue mais renseigné : imprimé sous son nom
 *       d'origine (relu sur sa ligne supprimée logiquement).</li>
 *   <li>Sections : sous-titres ; une section sans valeur imprimable disparaît.</li>
 *   <li>Culture : options renseignées, germes, antibiogramme aux libellés du
 *       laboratoire ({@code bio_antibiogram_labels}) et légende.</li>
 * </ul>
 *
 * @param provisoire compte-rendu non validé : bandeau, pas de signature
 * @param categories analyses par catégorie
 * @param conclusion conclusion générale ({@code reports.comment}), ou {@code null}
 * @param legende    libellés S/I/R, vide sans antibiogramme
 */
public record CompteRenduDeBiologie(boolean provisoire, List<Categorie> categories, String conclusion,
                                    List<Legende> legende) {

    static final String SANS_CATEGORIE = "Autres analyses";
    static final String EN_ATTENTE = "Résultat en attente";

    /** Libellé de l'indicateur « A » quand le laboratoire n'en a pas défini. */
    static final String LIBELLE_ANORMAL = "Anormal";

    /** Une catégorie et ses analyses. */
    public record Categorie(String nom, List<Analyse> analyses) {}

    /**
     * Une analyse.
     *
     * @param enAttente   rien d'imprimable n'est encore saisi
     * @param blocs       PANEL : paramètres hors section (titre {@code null}) puis sections
     * @param options     CULTURE : options renseignées
     * @param germes      CULTURE : germes isolés
     * @param commentaire commentaire de l'analyse, ou {@code null}
     */
    public record Analyse(String nom, String prelevement, boolean culture, boolean enAttente,
                          List<Bloc> blocs, List<Option> options, List<Germe> germes, String commentaire) {}

    /** Sous-titre ({@code null} hors section) et ses lignes. */
    public record Bloc(String titre, List<Ligne> lignes) {}

    /**
     * Une ligne du tableau Paramètre | Résultat | Unité | Valeurs de référence.
     *
     * @param indicateur libellé de l'indicateur hors normes, ou {@code null}
     */
    public record Ligne(String parametre, String resultat, String unite, String reference,
                        boolean anormal, String indicateur) {}

    /** Option de culture renseignée. */
    public record Option(String nom, String valeur) {}

    /**
     * Germe isolé et son antibiogramme.
     *
     * @param avecCmi      au moins une CMI renseignée : la colonne est imprimée
     * @param avecDiametre au moins un diamètre renseigné : la colonne est imprimée
     */
    public record Germe(String organisme, String quantite, boolean avecCmi, boolean avecDiametre,
                       List<Sensibilite> sensibilites) {}

    /**
     * Sensibilité à un antibiotique.
     *
     * @param interpretation valeur stockée (S, I, R)
     * @param libelle        libellé du laboratoire
     */
    public record Sensibilite(String antibiotique, String interpretation, String libelle, String cmi,
                              String diametre) {}

    /** Entrée de légende de l'antibiogramme. */
    public record Legende(String code, String libelle) {}

    /**
     * Construit le document.
     *
     * @param feuille                 feuille de saisie du bon
     * @param categoriesParAnalyse    nom de catégorie par analyse ({@code labTestId})
     * @param conclusion              conclusion générale, ou {@code null}
     * @param provisoire              compte-rendu non validé
     * @param libellesIndicateurs     {@code bio_flag_labels}
     * @param libellesAntibiogramme   {@code bio_antibiogram_labels}
     */
    public static CompteRenduDeBiologie construire(BiologyWorksheetDto feuille,
                                                   Map<UUID, String> categoriesParAnalyse,
                                                   String conclusion, boolean provisoire,
                                                   Map<String, String> libellesIndicateurs,
                                                   Map<String, String> libellesAntibiogramme) {
        Map<String, List<Analyse>> parCategorie = new LinkedHashMap<>();
        boolean avecAntibiogramme = false;
        for (BiologyWorksheetDto.Analysis a : feuille.analyses()) {
            Analyse analyse = a.kind() == BiologyKind.CULTURE
                    ? culture(a, libellesAntibiogramme)
                    : panel(a, libellesIndicateurs);
            avecAntibiogramme |= analyse.germes().stream().anyMatch(g -> !g.sensibilites().isEmpty());
            String categorie = texteOuNull(categoriesParAnalyse.get(a.labTestId()));
            parCategorie.computeIfAbsent(categorie != null ? categorie : SANS_CATEGORIE, k -> new ArrayList<>())
                    .add(analyse);
        }

        List<Categorie> categories = new ArrayList<>();
        parCategorie.forEach((nom, analyses) -> categories.add(new Categorie(nom, List.copyOf(analyses))));

        List<Legende> legende = new ArrayList<>();
        if (avecAntibiogramme) {
            for (String code : List.of("S", "I", "R")) {
                legende.add(new Legende(code, libellesAntibiogramme.getOrDefault(code, code)));
            }
        }
        return new CompteRenduDeBiologie(provisoire, List.copyOf(categories), texteOuNull(conclusion),
                List.copyOf(legende));
    }

    // ------------------------------------------------------------------ fiche (PANEL)

    private static Analyse panel(BiologyWorksheetDto.Analysis a, Map<String, String> libelles) {
        List<Bloc> blocs = new ArrayList<>();
        List<Ligne> horsSection = lignes(a.parameters(), libelles);
        if (!horsSection.isEmpty()) {
            blocs.add(new Bloc(null, horsSection));
        }
        if (a.sections() != null) {
            for (BiologyWorksheetDto.Section s : a.sections()) {
                List<Ligne> lignes = lignes(s.parameters(), libelles);
                if (!lignes.isEmpty()) {
                    blocs.add(new Bloc(s.title(), lignes));
                }
            }
        }
        return new Analyse(a.labTestName(), texteOuNull(a.specimenType()), false,
                blocs.isEmpty() || a.status() == BiologyAnalysisStatus.PENDING,
                List.copyOf(blocs), List.of(), List.of(), texteOuNull(a.comment()));
    }

    private static List<Ligne> lignes(List<BiologyWorksheetDto.ParameterRow> parametres, Map<String, String> libelles) {
        List<Ligne> lignes = new ArrayList<>();
        if (parametres == null) {
            return lignes;
        }
        for (BiologyWorksheetDto.ParameterRow p : parametres) {
            BiologyWorksheetDto.ParameterValue v = p.result();
            if (!p.printable() || v == null || v.value() == null || v.value().isBlank()) {
                continue;
            }
            String indicateur = indicateur(v.flag(), libelles);
            lignes.add(new Ligne(p.name(), valeur(v), texteOuNull(v.unitSnapshot()), reference(v),
                    indicateur != null, indicateur));
        }
        return lignes;
    }

    /** Valeur enregistrée ; un nombre prend la virgule décimale, sans être réarrondi. */
    static String valeur(BiologyWorksheetDto.ParameterValue v) {
        String brut = v.value().strip();
        return v.valueNumeric() != null ? brut.replace('.', ',') : brut;
    }

    /** Valeurs de référence copiées à la saisie : le texte figé, sinon les bornes figées. */
    static String reference(BiologyWorksheetDto.ParameterValue v) {
        String texte = texteOuNull(v.referenceSnapshot());
        if (texte != null) {
            return texte;
        }
        BigDecimal bas = v.lowSnapshot();
        BigDecimal haut = v.highSnapshot();
        return BiologyNumbers.intervalle(bas, haut, null);
    }

    /** Libellé d'un indicateur hors normes, ou {@code null} pour N et l'absence d'indicateur. */
    static String indicateur(BiologyFlag flag, Map<String, String> libelles) {
        if (flag == null || flag == BiologyFlag.N) {
            return null;
        }
        String libelle = texteOuNull(libelles.get(flag.name()));
        if (libelle != null) {
            return libelle;
        }
        return flag == BiologyFlag.A ? LIBELLE_ANORMAL : flag.name();
    }

    // ------------------------------------------------------------------ culture

    private static Analyse culture(BiologyWorksheetDto.Analysis a, Map<String, String> libelles) {
        List<Option> options = new ArrayList<>();
        if (a.cultureOptions() != null) {
            for (BiologyWorksheetDto.CultureOptionRow o : a.cultureOptions()) {
                String valeur = texteOuNull(o.value());
                if (valeur != null) {
                    options.add(new Option(o.name(), valeur));
                }
            }
        }
        List<Germe> germes = new ArrayList<>();
        if (a.isolates() != null) {
            for (BiologyWorksheetDto.Isolate g : a.isolates()) {
                List<Sensibilite> sensibilites = new ArrayList<>();
                boolean avecCmi = false;
                boolean avecDiametre = false;
                for (BiologyWorksheetDto.Antibiogram ab : g.antibiogram() != null
                        ? g.antibiogram() : List.<BiologyWorksheetDto.Antibiogram>of()) {
                    String cmi = texteOuNull(ab.mic());
                    String diametre = ab.diameterMm() != null ? BiologyNumbers.ecrire(ab.diameterMm(), null) : null;
                    avecCmi |= cmi != null;
                    avecDiametre |= diametre != null;
                    String code = ab.interpretation();
                    sensibilites.add(new Sensibilite(
                            ab.antibioticName() != null ? ab.antibioticName() : "Antibiotique retiré du référentiel",
                            code, code != null ? libelles.getOrDefault(code, code) : null, cmi, diametre));
                }
                germes.add(new Germe(g.organism(), texteOuNull(g.quantity()), avecCmi, avecDiametre,
                        List.copyOf(sensibilites)));
            }
        }
        return new Analyse(a.labTestName(), texteOuNull(a.specimenType()), true,
                (options.isEmpty() && germes.isEmpty()) || a.status() == BiologyAnalysisStatus.PENDING,
                List.of(), List.copyOf(options), List.copyOf(germes), texteOuNull(a.comment()));
    }

    private static String texteOuNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }
}

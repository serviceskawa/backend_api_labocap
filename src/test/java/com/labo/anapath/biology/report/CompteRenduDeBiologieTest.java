package com.labo.anapath.biology.report;

import com.labo.anapath.biology.results.BiologyAnalysisStatus;
import com.labo.anapath.biology.results.BiologyFlag;
import com.labo.anapath.biology.results.BiologyWorksheetDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.labo.anapath.biology.report.FeuillesDeTest.ANTIBIOGRAMME;
import static com.labo.anapath.biology.report.FeuillesDeTest.INDICATEURS;
import static com.labo.anapath.biology.report.FeuillesDeTest.ab;
import static com.labo.anapath.biology.report.FeuillesDeTest.culture;
import static com.labo.anapath.biology.report.FeuillesDeTest.feuille;
import static com.labo.anapath.biology.report.FeuillesDeTest.germe;
import static com.labo.anapath.biology.report.FeuillesDeTest.nombre;
import static com.labo.anapath.biology.report.FeuillesDeTest.option;
import static com.labo.anapath.biology.report.FeuillesDeTest.panel;
import static com.labo.anapath.biology.report.FeuillesDeTest.parametre;
import static com.labo.anapath.biology.report.FeuillesDeTest.section;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que le compte-rendu de biologie imprime, à partir de la feuille de saisie.
 */
class CompteRenduDeBiologieTest {

    private static CompteRenduDeBiologie construire(BiologyWorksheetDto f, Map<UUID, String> categories) {
        return CompteRenduDeBiologie.construire(f, categories, null, false, INDICATEURS, ANTIBIOGRAMME);
    }

    @Test
    @DisplayName("analyses regroupées par catégorie, dans l'ordre d'apparition ; sans catégorie → « Autres analyses »")
    void regroupement() {
        BiologyWorksheetDto.Analysis nfs = panel("NFS", BiologyAnalysisStatus.TECH_VALIDATED, null,
                List.of(nombre("Hémoglobine", "13.5", BiologyFlag.N, "g/dL", "13,0 – 17,0")), List.of());
        BiologyWorksheetDto.Analysis glycemie = panel("Glycémie", BiologyAnalysisStatus.TECH_VALIDATED, null,
                List.of(nombre("Glucose", "1.02", BiologyFlag.N, "g/L", "0,70 – 1,10")), List.of());
        BiologyWorksheetDto.Analysis vs = panel("VS", BiologyAnalysisStatus.TECH_VALIDATED, null,
                List.of(nombre("VS 1re heure", "8", BiologyFlag.N, "mm", "≤ 15")), List.of());
        BiologyWorksheetDto.Analysis creat = panel("Créatinine", BiologyAnalysisStatus.TECH_VALIDATED, null,
                List.of(nombre("Créatinine", "9.0", BiologyFlag.N, "mg/L", "7 – 13")), List.of());

        CompteRenduDeBiologie d = construire(feuille(List.of(nfs, glycemie, vs, creat)), Map.of(
                nfs.labTestId(), "Hématologie", glycemie.labTestId(), "Biochimie",
                vs.labTestId(), "Hématologie"));

        assertThat(d.categories()).extracting(CompteRenduDeBiologie.Categorie::nom)
                .containsExactly("Hématologie", "Biochimie", "Autres analyses");
        assertThat(d.categories().get(0).analyses()).extracting(CompteRenduDeBiologie.Analyse::nom)
                .containsExactly("NFS", "VS");
        assertThat(d.categories().get(2).analyses()).extracting(CompteRenduDeBiologie.Analyse::nom)
                .containsExactly("Créatinine");
    }

    @Test
    @DisplayName("non imprimable et sans valeur : omis ; section vide : omise ; sections en sous-titres")
    void omissions() {
        BiologyWorksheetDto.Analysis a = panel("Bilan", BiologyAnalysisStatus.TECH_VALIDATED, "  RAS  ",
                List.of(nombre("Visible", "5", BiologyFlag.N, "u", "1 – 9"),
                        parametre("Calcul interne", "42", new BigDecimal("42"), BiologyFlag.N, "u", null, false),
                        parametre("Jamais saisi", null, null, null, null, null, true)),
                List.of(section("Section vide", parametre("Caché", "1", BigDecimal.ONE, null, null, null, false)),
                        section("Ionogramme", nombre("Sodium", "140", BiologyFlag.N, "mmol/L", "135 – 145"))));

        CompteRenduDeBiologie.Analyse analyse = construire(feuille(List.of(a)), Map.of())
                .categories().get(0).analyses().get(0);

        assertThat(analyse.blocs()).extracting(CompteRenduDeBiologie.Bloc::titre)
                .containsExactly(null, "Ionogramme");
        assertThat(analyse.blocs().get(0).lignes()).extracting(CompteRenduDeBiologie.Ligne::parametre)
                .containsExactly("Visible");
        assertThat(analyse.commentaire()).isEqualTo("RAS");
        assertThat(analyse.enAttente()).isFalse();
    }

    @Test
    @DisplayName("unité et référence : les copies de la saisie, jamais le catalogue du jour ; virgule décimale")
    void copies() {
        BiologyWorksheetDto.Analysis a = panel("NFS", BiologyAnalysisStatus.TECH_VALIDATED, null,
                List.of(nombre("Hémoglobine", "12.50", BiologyFlag.N, "g/dL", "12,0 – 16,0"),
                        // Ancienne saisie sans texte figé : les bornes figées suffisent.
                        new BiologyWorksheetDto.ParameterRow(UUID.randomUUID(), null, "VGM", 1, null, null, null,
                                "fL (catalogue)", "ref catalogue", true, true, null,
                                new BiologyWorksheetDto.ParameterValue(UUID.randomUUID(), "85", new BigDecimal("85"),
                                        BiologyFlag.N, false, "fL", new BigDecimal("80.0000"),
                                        new BigDecimal("100.0000"), null, null, null))),
                List.of());

        List<CompteRenduDeBiologie.Ligne> lignes = construire(feuille(List.of(a)), Map.of())
                .categories().get(0).analyses().get(0).blocs().get(0).lignes();

        assertThat(lignes.get(0).resultat()).isEqualTo("12,50");
        assertThat(lignes.get(0).unite()).isEqualTo("g/dL");
        assertThat(lignes.get(0).reference()).isEqualTo("12,0 – 16,0");
        assertThat(lignes.get(1).unite()).isEqualTo("fL");
        assertThat(lignes.get(1).reference()).isEqualTo("80 – 100");
        assertThat(lignes).noneMatch(l -> l.unite().contains("catalogue") || l.reference().contains("catalogue"));
    }

    @Test
    @DisplayName("hors normes : gras + libellé du laboratoire ; A sans libellé → « Anormal » ; N → rien")
    void indicateurs() {
        Map<String, String> libelles = Map.of("L", "Bas", "H", "Élevé", "LL", "Critique bas", "HH", "Critique haut");
        BiologyWorksheetDto.Analysis a = panel("Bilan", BiologyAnalysisStatus.TECH_VALIDATED, null, List.of(
                nombre("Normal", "5", BiologyFlag.N, null, null),
                nombre("Haut", "20", BiologyFlag.H, null, null),
                nombre("Critique", "0.1", BiologyFlag.LL, null, null),
                parametre("Aspect", "Trouble", null, BiologyFlag.A, null, "Clair", true),
                parametre("Sans indicateur", "Jaune", null, null, null, null, true)), List.of());

        List<CompteRenduDeBiologie.Ligne> lignes = CompteRenduDeBiologie.construire(feuille(List.of(a)), Map.of(),
                null, false, libelles, ANTIBIOGRAMME).categories().get(0).analyses().get(0).blocs().get(0).lignes();

        assertThat(lignes).extracting(CompteRenduDeBiologie.Ligne::anormal)
                .containsExactly(false, true, true, true, false);
        assertThat(lignes).extracting(CompteRenduDeBiologie.Ligne::indicateur)
                .containsExactly(null, "Élevé", "Critique bas", "Anormal", null);
        // Pas de flèche : le marqueur est un texte.
        assertThat(lignes).extracting(CompteRenduDeBiologie.Ligne::indicateur)
                .allMatch(i -> i == null || !i.matches(".*[↑↓▲▼].*"));
    }

    @Test
    @DisplayName("paramètre retiré du catalogue mais renseigné : imprimé sous le nom fourni par la feuille")
    void parametreRetire() {
        BiologyWorksheetDto.Analysis a = panel("NFS", BiologyAnalysisStatus.TECH_VALIDATED, null, List.of(
                nombre("Hémoglobine", "14", BiologyFlag.N, "g/dL", "13 – 17")), List.of());
        // La feuille (B5) nomme l'orphelin d'après sa ligne supprimée logiquement.
        CompteRenduDeBiologie d = construire(feuille(List.of(a)), Map.of());
        assertThat(d.categories().get(0).analyses().get(0).blocs().get(0).lignes().get(0).parametre())
                .isEqualTo("Hémoglobine");
    }

    @Test
    @DisplayName("culture : options renseignées, germes, antibiogramme aux libellés du laboratoire, légende")
    void cultureEtAntibiogramme() {
        Map<String, String> libelles = Map.of("S", "Sensible", "I", "Intermédiaire", "R", "Résistante");
        BiologyWorksheetDto.Analysis u = culture("Uroculture",
                List.of(option("Aspect", "Trouble"), option("Leucocytes", " "), option("Hématies", "10/mm3")),
                List.of(germe("Escherichia coli", "10^5 UFC/mL",
                        ab("Amoxicilline", "R", ">32", null), ab("Ciprofloxacine", "S", null, "25.4")),
                        germe("Candida albicans", null)));

        CompteRenduDeBiologie d = CompteRenduDeBiologie.construire(feuille(List.of(u)), Map.of(), null, false,
                INDICATEURS, libelles);
        CompteRenduDeBiologie.Analyse c = d.categories().get(0).analyses().get(0);

        assertThat(c.culture()).isTrue();
        assertThat(c.options()).extracting(CompteRenduDeBiologie.Option::nom).containsExactly("Aspect", "Hématies");
        assertThat(c.germes()).hasSize(2);
        CompteRenduDeBiologie.Germe ecoli = c.germes().get(0);
        assertThat(ecoli.avecCmi()).isTrue();
        assertThat(ecoli.avecDiametre()).isTrue();
        assertThat(ecoli.sensibilites()).extracting(CompteRenduDeBiologie.Sensibilite::libelle)
                .containsExactly("Résistante", "Sensible");
        assertThat(ecoli.sensibilites().get(1).diametre()).isEqualTo("25,4");
        assertThat(c.germes().get(1).sensibilites()).isEmpty();
        assertThat(d.legende()).extracting(CompteRenduDeBiologie.Legende::libelle)
                .containsExactly("Sensible", "Intermédiaire", "Résistante");
    }

    @Test
    @DisplayName("provisoire : analyse non saisie → « en attente » ; pas de légende sans antibiogramme ; conclusion")
    void provisoire() {
        BiologyWorksheetDto.Analysis vide = panel("CRP", BiologyAnalysisStatus.PENDING, null, List.of(
                parametre("CRP", null, null, null, null, null, true)), List.of());
        BiologyWorksheetDto.Analysis cultureVide = culture("Hémoculture", List.of(), List.of());

        CompteRenduDeBiologie d = CompteRenduDeBiologie.construire(feuille(List.of(vide, cultureVide)), Map.of(),
                "  Contrôle dans 48 h.  ", true, INDICATEURS, ANTIBIOGRAMME);

        assertThat(d.provisoire()).isTrue();
        assertThat(d.categories().get(0).analyses()).allMatch(CompteRenduDeBiologie.Analyse::enAttente);
        assertThat(d.legende()).isEmpty();
        assertThat(d.conclusion()).isEqualTo("Contrôle dans 48 h.");
    }
}

package com.labo.anapath.biology.report;

import com.labo.anapath.biology.BiologyKind;
import com.labo.anapath.biology.ResultType;
import com.labo.anapath.biology.results.BiologyAnalysisStatus;
import com.labo.anapath.biology.results.BiologyFlag;
import com.labo.anapath.biology.results.BiologyValidationMode;
import com.labo.anapath.biology.results.BiologyWorksheetDto;
import com.labo.anapath.report.ReportStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fabrique de feuilles de saisie pour les tests du compte-rendu imprimé.
 */
final class FeuillesDeTest {

    static final Map<String, String> INDICATEURS = new LinkedHashMap<>(Map.of(
            "L", "Bas", "H", "Haut", "LL", "Critique bas", "HH", "Critique haut"));
    static final Map<String, String> ANTIBIOGRAMME = new LinkedHashMap<>(Map.of(
            "S", "Sensible", "I", "Intermédiaire", "R", "Résistant"));

    private FeuillesDeTest() {}

    /** Paramètre renseigné ; unité et référence figées passées explicitement. */
    static BiologyWorksheetDto.ParameterRow parametre(String nom, String valeur, BigDecimal nombre, BiologyFlag flag,
                                                      String uniteFigee, String referenceFigee, boolean imprimable) {
        return new BiologyWorksheetDto.ParameterRow(UUID.randomUUID(), null, nom, 0,
                nombre != null ? ResultType.NUMERIC : ResultType.TEXT, null, (short) 1,
                "unité-du-catalogue-du-jour", "référence-du-catalogue-du-jour", imprimable, true,
                null,
                valeur == null ? null : new BiologyWorksheetDto.ParameterValue(UUID.randomUUID(), valeur, nombre, flag,
                        false, uniteFigee, null, null, null, null, referenceFigee));
    }

    static BiologyWorksheetDto.ParameterRow nombre(String nom, String valeur, BiologyFlag flag, String unite,
                                                   String reference) {
        return parametre(nom, valeur, new BigDecimal(valeur), flag, unite, reference, true);
    }

    static BiologyWorksheetDto.Analysis panel(String nom, BiologyAnalysisStatus statut, String commentaire,
                                              List<BiologyWorksheetDto.ParameterRow> horsSection,
                                              List<BiologyWorksheetDto.Section> sections) {
        return new BiologyWorksheetDto.Analysis(UUID.randomUUID(), UUID.randomUUID(), nom, null, BiologyKind.PANEL,
                "Sang total", statut, commentaire, null, null, null, null, null, null, false, 0, 0,
                sections, horsSection, List.of(), List.of());
    }

    static BiologyWorksheetDto.Section section(String titre, BiologyWorksheetDto.ParameterRow... lignes) {
        return new BiologyWorksheetDto.Section(UUID.randomUUID(), titre, 0, List.of(lignes));
    }

    static BiologyWorksheetDto.Analysis culture(String nom, List<BiologyWorksheetDto.CultureOptionRow> options,
                                                List<BiologyWorksheetDto.Isolate> germes) {
        return new BiologyWorksheetDto.Analysis(UUID.randomUUID(), UUID.randomUUID(), nom, null, BiologyKind.CULTURE,
                "Urines", BiologyAnalysisStatus.TECH_VALIDATED, null, null, null, null, null, null, null, false, 0, 0,
                List.of(), List.of(), options, germes);
    }

    static BiologyWorksheetDto.CultureOptionRow option(String nom, String valeur) {
        return new BiologyWorksheetDto.CultureOptionRow(UUID.randomUUID(), nom, null, 0, UUID.randomUUID(), valeur);
    }

    static BiologyWorksheetDto.Isolate germe(String organisme, String quantite,
                                             BiologyWorksheetDto.Antibiogram... antibiogramme) {
        return new BiologyWorksheetDto.Isolate(UUID.randomUUID(), organisme, quantite, 0, List.of(antibiogramme));
    }

    static BiologyWorksheetDto.Antibiogram ab(String nom, String interpretation, String cmi, String diametre) {
        return new BiologyWorksheetDto.Antibiogram(UUID.randomUUID(), UUID.randomUUID(), nom, null, interpretation,
                cmi, diametre != null ? new BigDecimal(diametre) : null);
    }

    static BiologyWorksheetDto feuille(List<BiologyWorksheetDto.Analysis> analyses) {
        return new BiologyWorksheetDto(UUID.randomUUID(), "26-0100", false, LocalDate.of(2026, 9, 1),
                new BiologyWorksheetDto.Patient(UUID.randomUUID(), "PAT-1", "Koffi", "AGBO", "AGBO Koffi",
                        "Masculin", "M", null, 40, "YEARS", 14610),
                new BiologyWorksheetDto.Report(UUID.randomUUID(), "CO26-0100", ReportStatus.VALIDATED),
                new BiologyWorksheetDto.Settings(BiologyValidationMode.TWO_STEP, ANTIBIOGRAMME, INDICATEURS),
                analyses, List.of());
    }

    /** Numération de {@code n} paramètres, pour forcer un tableau sur plusieurs pages. */
    static BiologyWorksheetDto.Analysis grandPanel(String nom, int n) {
        List<BiologyWorksheetDto.ParameterRow> hors = new ArrayList<>();
        List<BiologyWorksheetDto.ParameterRow> sec = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            BiologyFlag f = i % 7 == 0 ? BiologyFlag.H : i % 11 == 0 ? BiologyFlag.LL : BiologyFlag.N;
            BiologyWorksheetDto.ParameterRow p = nombre("Paramètre n° " + i, i + ".5", f, "mmol/L",
                    i % 5 == 0 ? "≥ 3,0" : "1,0 – 9,5");
            (i <= n / 2 ? hors : sec).add(p);
        }
        return panel(nom, BiologyAnalysisStatus.TECH_VALIDATED, "Contrôle sur second prélèvement conseillé.",
                hors, List.of(new BiologyWorksheetDto.Section(UUID.randomUUID(), "Seconde partie", 0, sec)));
    }
}

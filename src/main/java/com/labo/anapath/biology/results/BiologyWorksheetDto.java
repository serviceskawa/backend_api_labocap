package com.labo.anapath.biology.results;

import com.labo.anapath.biology.BiologyKind;
import com.labo.anapath.biology.ResultType;
import com.labo.anapath.report.ReportStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Feuille de saisie d'un bon de biologie : le patient, le compte-rendu, les
 * réglages utiles à l'écran et, par analyse, la fiche avec valeurs et indicateurs.
 *
 * @param testOrderId     bon
 * @param orderCode       code du bon
 * @param urgent          bon urgent
 * @param prelevementDate date du prélèvement (référence de l'âge)
 * @param patient         patient
 * @param report          compte-rendu, ou {@code null}
 * @param settings        réglages de biologie de la succursale
 * @param analyses        analyses, dans l'ordre du bon
 * @param antibiotics     référentiel des antibiotiques (vide sans analyse CULTURE)
 */
public record BiologyWorksheetDto(
        UUID testOrderId,
        String orderCode,
        boolean urgent,
        LocalDate prelevementDate,
        Patient patient,
        Report report,
        Settings settings,
        List<Analysis> analyses,
        List<AntibioticRef> antibiotics
) {

    /**
     * @param sex       {@code "M"}, {@code "F"} ou {@code null} (normalisé)
     * @param genre     sexe tel que saisi sur la fiche
     * @param ageUnit   {@code "YEARS"} ou {@code "MONTHS"}
     * @param ageInDays âge en jours à la date du prélèvement (sert aux valeurs de référence), ou {@code null}
     */
    public record Patient(UUID id, String code, String firstname, String lastname, String fullName,
                          String genre, String sex, LocalDate birthday, Integer age, String ageUnit,
                          Integer ageInDays) {}

    /** Compte-rendu du bon. */
    public record Report(UUID id, String code, ReportStatus status) {}

    /**
     * @param validationMode    {@code TWO_STEP} ou {@code ONE_STEP}
     * @param antibiogramLabels libellés S/I/R
     * @param flagLabels        libellés L/H/LL/HH
     */
    public record Settings(BiologyValidationMode validationMode, Map<String, String> antibiogramLabels,
                           Map<String, String> flagLabels) {}

    /**
     * Une analyse du bon.
     *
     * @param id              ligne de saisie
     * @param editable        {@code false} si validée techniquement ou compte-rendu validé/livré
     * @param expectedValues  paramètres (PANEL) ou options (CULTURE) à renseigner
     * @param enteredValues   parmi eux, ceux qui ont une valeur
     * @param sections        PANEL : sections et leurs paramètres
     * @param parameters      PANEL : paramètres hors section, puis valeurs de paramètres retirés du catalogue
     * @param cultureOptions  CULTURE : options retenues et leurs valeurs
     * @param isolates        CULTURE : germes et antibiogrammes
     */
    public record Analysis(UUID id, UUID labTestId, String labTestName, String labTestCode, BiologyKind kind,
                           String specimenType, BiologyAnalysisStatus status, String comment,
                           UUID enteredBy, String enteredByName, LocalDateTime enteredAt,
                           UUID techValidatedBy, String techValidatedByName, LocalDateTime techValidatedAt,
                           boolean editable, int expectedValues, int enteredValues,
                           List<Section> sections, List<ParameterRow> parameters,
                           List<CultureOptionRow> cultureOptions, List<Isolate> isolates) {}

    /** Section de fiche. */
    public record Section(UUID id, String title, int position, List<ParameterRow> parameters) {}

    /**
     * Paramètre et sa valeur.
     *
     * @param unit          unité actuelle du catalogue
     * @param referenceText valeurs de référence en texte libre du catalogue
     * @param range         plage applicable à ce patient aujourd'hui, ou {@code null}
     * @param result        valeur enregistrée, ou {@code null}
     */
    public record ParameterRow(UUID parameterId, String code, String name, int position, ResultType resultType,
                               List<String> choices, Short decimals, String unit, String referenceText,
                               boolean printable, boolean flaggable, AppliedRange range, ParameterValue result) {}

    /**
     * Plage de référence résolue pour le patient.
     *
     * @param display bornes normales telles qu'imprimées (« 12,0 – 16,0 »)
     */
    public record AppliedRange(UUID id, BigDecimal low, BigDecimal high, BigDecimal criticalLow,
                               BigDecimal criticalHigh, String label, String display) {}

    /** Valeur enregistrée, avec l'unité et les valeurs de référence figées à la saisie. */
    public record ParameterValue(UUID id, String value, BigDecimal valueNumeric, BiologyFlag flag,
                                 boolean flagOverridden, String unitSnapshot, BigDecimal lowSnapshot,
                                 BigDecimal highSnapshot, BigDecimal criticalLowSnapshot,
                                 BigDecimal criticalHighSnapshot, String referenceSnapshot) {}

    /** Option de culture et sa valeur ({@code null} si non renseignée). */
    public record CultureOptionRow(UUID cultureOptionId, String name, List<String> choices, int position,
                                   UUID resultId, String value) {}

    /** Germe isolé. */
    public record Isolate(UUID id, String organism, String quantity, int position, List<Antibiogram> antibiogram) {}

    /** Sensibilité à un antibiotique ({@code interpretation} : S, I ou R). */
    public record Antibiogram(UUID id, UUID antibioticId, String antibioticName, String antibioticCode,
                              String interpretation, String mic, BigDecimal diameterMm) {}

    /** Antibiotique du référentiel, pour composer l'antibiogramme. */
    public record AntibioticRef(UUID id, String name, String code, String family, int position) {}
}

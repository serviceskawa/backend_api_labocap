package com.labo.anapath.biology;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Fiche de paramètres d'une analyse PANEL : sections, paramètres et valeurs de
 * référence, dans l'ordre d'affichage.
 *
 * @param labTestId   identifiant de l'analyse
 * @param labTestName nom de l'analyse
 * @param sections    sections, chacune avec ses paramètres
 * @param parameters  paramètres hors section
 */
public record BiologySheetResponseDto(
        UUID labTestId,
        String labTestName,
        List<Section> sections,
        List<Parameter> parameters
) {

    /**
     * Section d'une fiche.
     *
     * @param id         identifiant
     * @param title      titre
     * @param position   rang d'affichage
     * @param parameters paramètres de la section
     */
    public record Section(UUID id, String title, int position, List<Parameter> parameters) {}

    /**
     * Paramètre d'une fiche.
     *
     * @param id                          identifiant
     * @param sectionId                   section, ou {@code null}
     * @param code                        code court
     * @param name                        libellé
     * @param position                    rang d'affichage
     * @param resultType                  forme du résultat
     * @param choices                     choix proposés (CHOICE)
     * @param decimals                    décimales affichées
     * @param unitMeasurementId           unité
     * @param unitMeasurementName         nom de l'unité
     * @param unitMeasurementAbbreviation abréviation de l'unité
     * @param referenceText               valeurs de référence en texte libre
     * @param printable                   imprimé sur le compte-rendu
     * @param flaggable                   indicateurs calculés
     * @param ranges                      valeurs de référence chiffrées
     */
    public record Parameter(
            UUID id,
            UUID sectionId,
            String code,
            String name,
            int position,
            ResultType resultType,
            List<String> choices,
            Short decimals,
            UUID unitMeasurementId,
            String unitMeasurementName,
            String unitMeasurementAbbreviation,
            String referenceText,
            boolean printable,
            boolean flaggable,
            List<Range> ranges
    ) {}

    /**
     * Plage de valeurs de référence.
     *
     * @param id           identifiant
     * @param sex          {@code "M"}, {@code "F"} ou {@code null}
     * @param ageMinDays   âge minimal en jours, inclus
     * @param ageMaxDays   âge maximal en jours, exclu
     * @param low          borne basse
     * @param high         borne haute
     * @param criticalLow  seuil critique bas
     * @param criticalHigh seuil critique haut
     * @param label        libellé de la population
     * @param position     rang d'affichage
     */
    public record Range(
            UUID id,
            String sex,
            Integer ageMinDays,
            Integer ageMaxDays,
            BigDecimal low,
            BigDecimal high,
            BigDecimal criticalLow,
            BigDecimal criticalHigh,
            String label,
            int position
    ) {}
}

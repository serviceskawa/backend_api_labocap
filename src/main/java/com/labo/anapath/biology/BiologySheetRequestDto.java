package com.labo.anapath.biology;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fiche de paramètres complète d'une analyse PANEL, telle que l'éditeur l'envoie
 * ({@code PUT /api/v1/biology-parameters/sheet/{labTestId}}).
 *
 * <p>La fiche est remplacée en bloc, par identifiant :
 * <ul>
 *   <li>un élément avec {@code id} met à jour l'élément existant (qui doit appartenir
 *       à cette analyse) ;</li>
 *   <li>un élément sans {@code id} est créé ;</li>
 *   <li>un élément existant absent de la requête est supprimé (logiquement).</li>
 * </ul>
 * <b>L'ordre des listes fait foi</b> : la position de chaque section, paramètre et
 * plage est son rang dans la liste qui le contient.</p>
 */
@Getter
@Setter
public class BiologySheetRequestDto {

    /** Sections, chacune avec ses paramètres. */
    @Valid
    private List<SectionRequest> sections = new ArrayList<>();

    /** Paramètres hors section, affichés après les sections. */
    @Valid
    private List<ParameterRequest> parameters = new ArrayList<>();

    /** Section d'une fiche. */
    @Getter
    @Setter
    public static class SectionRequest {

        /** Identifiant d'une section existante, ou {@code null} pour en créer une. */
        private UUID id;

        /** Titre affiché. */
        @NotBlank(message = "Le titre de la section est obligatoire")
        @Size(max = 200, message = "Le titre de la section ne doit pas dépasser 200 caractères")
        private String title;

        /** Paramètres de la section, dans l'ordre d'affichage. */
        @Valid
        private List<ParameterRequest> parameters = new ArrayList<>();
    }

    /** Paramètre d'une fiche. */
    @Getter
    @Setter
    public static class ParameterRequest {

        /** Identifiant d'un paramètre existant, ou {@code null} pour en créer un. */
        private UUID id;

        /** Code court, unique dans l'analyse (facultatif). */
        @Size(max = 50, message = "Le code du paramètre ne doit pas dépasser 50 caractères")
        private String code;

        /** Libellé du paramètre. */
        @NotBlank(message = "Le nom du paramètre est obligatoire")
        @Size(max = 200, message = "Le nom du paramètre ne doit pas dépasser 200 caractères")
        private String name;

        /** Forme du résultat. */
        @NotNull(message = "Le type de résultat est obligatoire")
        private ResultType resultType;

        /** Choix proposés ; obligatoires pour {@link ResultType#CHOICE}, ignorés sinon. */
        private List<String> choices;

        /** Décimales affichées (0 à 6) pour un résultat chiffré. */
        private Short decimals;

        /** Unité du résultat. */
        private UUID unitMeasurementId;

        /** Valeurs de référence en texte libre. */
        private String referenceText;

        /** Imprimé sur le compte-rendu ({@code true} par défaut). */
        private Boolean printable;

        /** Indicateurs calculés ({@code true} par défaut). */
        private Boolean flaggable;

        /** Valeurs de référence chiffrées ; réservées aux paramètres {@link ResultType#NUMERIC}. */
        @Valid
        private List<RangeRequest> ranges = new ArrayList<>();
    }

    /** Plage de valeurs de référence. */
    @Getter
    @Setter
    public static class RangeRequest {

        /** Identifiant d'une plage existante, ou {@code null} pour en créer une. */
        private UUID id;

        /** {@code "M"}, {@code "F"} ou {@code null} (les deux sexes). */
        private String sex;

        /** Âge minimal en jours, inclus. */
        private Integer ageMinDays;

        /** Âge maximal en jours, exclu. */
        private Integer ageMaxDays;

        /** Borne basse de la normale. */
        private BigDecimal low;

        /** Borne haute de la normale. */
        private BigDecimal high;

        /** Seuil critique bas. */
        private BigDecimal criticalLow;

        /** Seuil critique haut. */
        private BigDecimal criticalHigh;

        /** Libellé de la population (ex. : « Nouveau-né »). */
        @Size(max = 100, message = "Le libellé de la plage ne doit pas dépasser 100 caractères")
        private String label;
    }
}

package com.labo.anapath.biology.results;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Résultats d'une analyse CULTURE
 * ({@code PUT /api/v1/biology-results/orders/{testOrderId}/analyses/{labTestId}/culture}).
 *
 * <ul>
 *   <li>{@code options} : comme les valeurs d'une fiche — une option listée avec une
 *       valeur est enregistrée, avec une valeur vide effacée, absente laissée telle
 *       quelle ; {@code null} ne touche à rien ;</li>
 *   <li>{@code isolates} : <b>liste complète</b> des germes — un germe avec {@code id}
 *       est mis à jour, sans {@code id} créé, un germe existant absent supprimé avec son
 *       antibiogramme ; l'ordre de la liste fait foi. {@code null} ne touche à rien,
 *       {@code []} supprime tous les germes ;</li>
 *   <li>{@code antibiogram} d'un germe : liste complète, par antibiotique ;</li>
 *   <li>{@code comment} : remplacé tel qu'envoyé.</li>
 * </ul>
 */
@Getter
@Setter
public class BiologyCultureResultsRequestDto {

    /** Valeurs des options de culture. */
    @Valid
    private List<OptionValue> options;

    /** Germes isolés, liste complète. */
    @Valid
    private List<Isolate> isolates;

    /** Commentaire de l'analyse ; vide ou {@code null} l'efface. */
    @Size(max = 5000, message = "Le commentaire ne doit pas dépasser 5000 caractères")
    private String comment;

    /** Valeur d'une option de culture. */
    @Getter
    @Setter
    public static class OptionValue {

        /** Option retenue par l'analyse. */
        @NotNull(message = "L'option de culture est obligatoire")
        private UUID cultureOptionId;

        /** Valeur ; vide = effacer. */
        @Size(max = 2000, message = "Une valeur ne doit pas dépasser 2000 caractères")
        private String value;
    }

    /** Germe isolé. */
    @Getter
    @Setter
    public static class Isolate {

        /** Germe existant de cette analyse, ou {@code null} pour en créer un. */
        private UUID id;

        /** Germe identifié. */
        @NotBlank(message = "Le germe isolé est obligatoire")
        @Size(max = 200, message = "Le germe ne doit pas dépasser 200 caractères")
        private String organism;

        /** Quantité, en texte libre. */
        @Size(max = 100, message = "La quantité ne doit pas dépasser 100 caractères")
        private String quantity;

        /** Antibiogramme, liste complète. */
        @Valid
        private List<Antibiogram> antibiogram;
    }

    /** Sensibilité à un antibiotique. */
    @Getter
    @Setter
    public static class Antibiogram {

        /** Antibiotique du référentiel. */
        @NotNull(message = "L'antibiotique est obligatoire")
        private UUID antibioticId;

        /** S, I ou R. */
        @NotBlank(message = "L'interprétation (S, I ou R) est obligatoire")
        private String interpretation;

        /** Concentration minimale inhibitrice, en texte. */
        @Size(max = 20, message = "La CMI ne doit pas dépasser 20 caractères")
        private String mic;

        /** Diamètre d'inhibition, en millimètres. */
        @DecimalMin(value = "0", message = "Le diamètre ne peut pas être négatif")
        @DecimalMax(value = "9999.9", message = "Le diamètre est trop grand")
        private BigDecimal diameterMm;
    }
}

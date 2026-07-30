package com.labo.anapath.test;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO de requête pour la création et la mise à jour d'une analyse du catalogue.
 */
@Getter
@Setter
public class LabTestRequestDto {

    /** Nom de l'analyse (obligatoire, ex. : "Numération Formule Sanguine"). */
    @NotBlank(message = "Le nom de l'analyse est obligatoire")
    private String name;

    /** Code court optionnel de l'analyse (ex. : "NFS"). */
    private String code;

    /** Prix de facturation en FCFA (obligatoire, doit être positif ou nul). */
    @NotNull(message = "Le prix est obligatoire")
    @DecimalMin(value = "0.0", message = "Le prix doit être positif")
    @DecimalMin(value = "0", inclusive = false,
                message = "Veuillez renseigner un prix supérieur à zéro")
    private BigDecimal price;

    /** Valeurs normales de référence en texte libre (ex. : "4,5–5,5 T/L"). */
    private String normalValue;

    /** Identifiant de la catégorie d'appartenance (optionnel). */
    private UUID categoryTestId;

    /** Identifiant de l'unité de mesure des résultats (optionnel). */
    private UUID unitMeasurementId;

    /** Statut de l'analyse : {@code ACTIF} (défaut) ou {@code INACTIF}. */
    private String status = "ACTIF";
}

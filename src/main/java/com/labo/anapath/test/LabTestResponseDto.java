package com.labo.anapath.test;

import com.labo.anapath.biology.BiologyKind;
import com.labo.anapath.common.Discipline;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de réponse représentant une analyse du catalogue exposée par l'API.
 *
 * <p>Les informations de catégorie et d'unité de mesure sont dénormalisées
 * (id + nom) pour éviter des requêtes supplémentaires côté client.</p>
 *
 * @param id                  identifiant unique de l'analyse
 * @param name                nom de l'analyse
 * @param code                code court optionnel
 * @param price               prix de facturation en FCFA
 * @param normalValue         valeurs normales de référence
 * @param status              statut de l'analyse ({@code ACTIF} ou {@code INACTIF})
 * @param categoryTestId      identifiant de la catégorie d'appartenance
 * @param categoryTestName    nom de la catégorie d'appartenance
 * @param unitMeasurementId   identifiant de l'unité de mesure
 * @param unitMeasurementName nom de l'unité de mesure
 * @param branchId            identifiant de la succursale propriétaire
 * @param createdAt           date et heure de création
 * @param discipline          discipline de l'analyse (PATHOLOGY ou BIOLOGY)
 * @param biologyKind         nature d'une analyse de biologie (PANEL, CULTURE), sinon {@code null}
 * @param specimenType        type d'échantillon attendu (biologie)
 */
public record LabTestResponseDto(
        UUID id,
        String name,
        String code,
        BigDecimal price,
        String normalValue,
        String status,
        UUID categoryTestId,
        String categoryTestName,
        UUID unitMeasurementId,
        String unitMeasurementName,
        UUID branchId,
        LocalDateTime createdAt,
        Discipline discipline,
        BiologyKind biologyKind,
        String specimenType
) {}

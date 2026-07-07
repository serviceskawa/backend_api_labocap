package com.labo.anapath.patient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de réponse représentant un dossier patient renvoyé au client HTTP.
 * <p>
 * Expose l'ensemble des données du patient sans les champs techniques internes
 * ({@code updatedAt}, {@code deletedAt}, auteurs de modification).
 * </p>
 *
 * @param id          identifiant unique du patient
 * @param code        code patient (référence métier unique par agence)
 * @param firstname   prénom
 * @param lastname    nom de famille
 * @param genre       genre ("M", "F", etc. — peut être {@code null})
 * @param telephone1  téléphone principal (peut être {@code null})
 * @param telephone2  téléphone secondaire (peut être {@code null})
 * @param adresse     adresse physique (peut être {@code null})
 * @param age         âge (peut être {@code null})
 * @param yearOrMonth {@code true} si l'âge est en années, {@code false} si en mois
 * @param birthday    date de naissance (peut être {@code null})
 * @param profession  profession (peut être {@code null})
 * @param langue      langue parlée (peut être {@code null})
 * @param email       adresse e-mail (peut être {@code null})
 * @param branchId      identifiant de l'agence
 * @param createdAt     date et heure de création du dossier
 * @param totalInvoiced total facturé au patient (somme des factures) — renseigné dans la liste
 * @param totalPaid     total réglé (factures payées) — renseigné dans la liste
 * @param totalUnpaid   total restant dû (factures impayées) — renseigné dans la liste
 */
public record PatientResponseDto(
        UUID id,
        String code,
        String firstname,
        String lastname,
        String genre,
        String telephone1,
        String telephone2,
        String adresse,
        Integer age,
        Boolean yearOrMonth,
        LocalDate birthday,
        String profession,
        String langue,
        String email,
        UUID branchId,
        LocalDateTime createdAt,
        BigDecimal totalInvoiced,
        BigDecimal totalPaid,
        BigDecimal totalUnpaid
) {}

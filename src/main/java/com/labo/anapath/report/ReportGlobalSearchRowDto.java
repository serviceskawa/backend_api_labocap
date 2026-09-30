package com.labo.anapath.report;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO d'une ligne du tableau "Recherche globale" (réplique Laravel reports.export / search).
 * Contient tous les champs nécessaires aux 10 colonnes du tableau Laravel :
 * Code Rapport, Code Examen, Type, Contrat, Patient, Médecin, Hôpital, Réf hôpital, Date, Urgent.
 */
public record ReportGlobalSearchRowDto(
        UUID reportId,
        String codeReport,
        UUID testOrderId,
        String codeExamen,
        String typeExamen,
        String contractName,
        UUID patientId,
        String patientFirstname,
        String patientLastname,
        UUID doctorId,
        String doctorName,
        UUID hospitalId,
        String hospitalName,
        String referenceHospital,
        LocalDateTime dateCreation,
        Boolean isUrgent,
        /**
         * Discipline du compte-rendu. La recherche globale couvre les deux
         * disciplines : l'écran doit savoir vers quel circuit ouvrir la ligne.
         */
        com.labo.anapath.common.Discipline discipline
) {}

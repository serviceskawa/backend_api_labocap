package com.labo.anapath.testorder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Une demande antérieure du même patient.
 *
 * <p>Le strict nécessaire pour se repérer dans une chronologie : le code, la
 * date, l'état. Pas de contenu médical — l'historique sert à savoir qu'un
 * dossier existe et quand, pas à le lire de loin. Qui veut l'ouvrir tape
 * dessus, et retombe sur la consultation ordinaire, avec ses propres
 * contrôles.</p>
 *
 * @param testOrderId  la demande, pour l'ouvrir
 * @param code         ce que le médecin lit et reconnaît
 * @param createdAt    quand elle a été enregistrée
 * @param status       où en est la demande
 * @param courante     est-ce le dossier depuis lequel on consulte cet
 *                     historique ? Repéré pour qu'on se situe dans la
 *                     chronologie plutôt que de chercher lequel on regardait.
 */
public record HistoriquePatientDto(
        UUID testOrderId,
        String code,
        LocalDateTime createdAt,
        String status,
        boolean courante) {
}

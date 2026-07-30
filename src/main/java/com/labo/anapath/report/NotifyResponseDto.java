package com.labo.anapath.report;

import java.util.UUID;

/**
 * Résultat de la notification automatique d'un patient (appel vocal ou SMS).
 *
 * <p>Réplique de l'action Laravel {@code ReportController::callOrSendSms()}, qui choisit
 * seule le canal : SMS si le bon d'examen porte l'option, sinon appel vocal — et
 * uniquement dans la plage horaire autorisée. Le champ {@code channel} dit ce qui a
 * réellement été fait, là où Laravel affiche « Effectué avec succès » même quand rien
 * n'est parti.
 *
 * @param channel  canal effectivement utilisé : {@code SMS}, {@code CALL} ou {@code NONE}
 * @param reportId identifiant du compte rendu concerné
 * @param appelId  identifiant de l'appel OurVoice, seulement si {@code channel = CALL}
 * @param message  libellé prêt à afficher expliquant l'issue
 */
public record NotifyResponseDto(
        String channel,
        UUID reportId,
        String appelId,
        String message) {
}

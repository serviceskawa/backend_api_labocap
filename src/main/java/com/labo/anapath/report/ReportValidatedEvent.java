package com.labo.anapath.report;

import java.util.UUID;

/**
 * Un compte-rendu vient de passer à l'état validé : le résultat est disponible
 * au retrait pour le patient.
 *
 * <p>Publié par {@code ReportServiceImpl} sur les trois chemins qui posent ce
 * statut — création directe en validé, enregistrement du formulaire, et l'action
 * dédiée {@code POST /reports/{id}/validate} — et consommé après commit par
 * {@link ReportValidationNotifier}, qui prévient le patient.</p>
 *
 * <p><b>Pourquoi un événement.</b> La validation engage un diagnostic ; l'appel
 * vocal n'est qu'un avis de disponibilité. Les lier dans la même transaction
 * ferait perdre une signature de pathologiste parce qu'OurVoice n'a pas répondu.</p>
 *
 * @param reportId identifiant du compte-rendu validé
 * @param userId   utilisateur ayant validé, pour la journalisation de l'envoi
 */
public record ReportValidatedEvent(UUID reportId, UUID userId) {
}

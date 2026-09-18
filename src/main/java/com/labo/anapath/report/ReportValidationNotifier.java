package com.labo.anapath.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Prévient le patient, dès la validation de son compte-rendu, que son résultat
 * est disponible au retrait.
 *
 * <p>L'avis passait jusqu'ici par un clic d'agent sur {@code POST /reports/{id}/notify} :
 * un compte-rendu validé un vendredi soir pouvait rester des jours sans que le
 * patient le sache. L'écouteur automatise ce même geste, sans en changer les
 * règles — il délègue à {@link NotificationService#notifyPatient} qui arbitre
 * seul entre SMS et appel vocal et respecte la plage horaire autorisée.</p>
 *
 * <p>Le bouton manuel reste en place : il sert aux relances, et aux comptes-rendus
 * validés hors de la plage d'appel, pour lesquels rien n'est parti.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReportValidationNotifier {

    private final NotificationService notificationService;

    /**
     * Lance la notification du patient après validation.
     *
     * <p>{@code AFTER_COMMIT} : on n'appelle pas un patient pour un compte-rendu
     * dont l'enregistrement a fini par échouer. {@code @Async} : l'appel HTTP à
     * OurVoice ne doit pas faire patienter le pathologiste qui vient de signer.
     * Toute erreur s'arrête ici, journalisée — la validation, elle, est acquise.</p>
     */
    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReportValidated(ReportValidatedEvent event) {
        try {
            NotifyResponseDto resultat =
                    notificationService.notifyPatient(event.reportId(), event.userId());
            log.info("Compte-rendu {} validé : notification patient → {} ({})",
                    event.reportId(), resultat.channel(), resultat.message());
        } catch (RuntimeException e) {
            log.error("Compte-rendu {} : notification automatique du patient échouée : {}",
                    event.reportId(), e.getMessage());
        }
    }
}

package com.labo.anapath.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reprend, chaque matin à 8h, les avis de disponibilité qui n'ont pas pu partir.
 *
 * <p>Un compte-rendu validé après 18h ne déclenche aucun appel : on ne réveille
 * pas un patient — voir {@link NotificationServiceImpl#notifyPatient}. Jusqu'ici
 * ces avis attendaient un clic d'agent, et un compte-rendu signé le vendredi à
 * 19h restait muet jusqu'au lundi. Cette reprise les rattrape à l'ouverture.</p>
 *
 * <p><b>Deux garde-fous.</b> La fenêtre de {@value #FENETRE_JOURS} jours empêche
 * qu'un arriéré — panne, reprise de service — ne parte d'un seul coup, et le lot
 * de {@value #LOT_MAX} plafonne ce qu'une exécution peut envoyer. Passé ces
 * limites, l'avis se fait au comptoir : mieux vaut un patient non rappelé qu'une
 * campagne d'appels partie de travers.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RattrapageAvisPatientJob {

    /** Ancienneté maximale d'une validation encore reprise, en jours. */
    private static final int FENETRE_JOURS = 3;

    /** Nombre maximal d'avis envoyés par exécution. */
    private static final int LOT_MAX = 100;

    private final ReportRepository reportRepository;
    private final NotificationService notificationService;

    /**
     * Exécution quotidienne, à l'ouverture du laboratoire.
     *
     * <p>Le fuseau est explicite : le conteneur de production tourne en UTC, où
     * « 8h » tomberait à 9h pour le patient — soit une heure d'attente de plus
     * chaque matin, et un envoi hors de la plage d'appel un jour de bascule.</p>
     *
     * <p>L'horaire est configurable ({@code REPORT_NOTIFY_RETRY_CRON}) pour deux
     * raisons : le laboratoire n'ouvre pas partout à la même heure, et une
     * recette ne peut pas attendre le lendemain matin pour vérifier la reprise.
     * La valeur {@code -} désactive la tâche.</p>
     */
    @Scheduled(cron = "${app.report.notify-retry.cron:0 0 8 * * *}", zone = "Africa/Porto-Novo")
    public void rattraperAvisDuMatin() {
        int envoyes = rattraperAvisEnAttente();
        if (envoyes > 0) {
            log.info("[scheduler] Reprise des avis patients : {} avis envoyé(s)", envoyes);
        }
    }

    /**
     * Envoie les avis en attente et renvoie le nombre d'envois effectués.
     *
     * <p>Publique et distincte du déclencheur planifié, pour être appelable
     * depuis un test sans dépendre de l'heure qu'il est.</p>
     *
     * <p>Chaque compte-rendu est traité isolément : {@code notifyPatient} porte
     * sa propre transaction, et une erreur — patient sans numéro, OurVoice
     * injoignable — ne doit pas priver d'avis les patients suivants du lot.</p>
     *
     * @return le nombre d'avis réellement partis
     */
    public int rattraperAvisEnAttente() {
        LocalDateTime depuis = LocalDateTime.now().minusDays(FENETRE_JOURS);
        List<Report> enAttente =
                reportRepository.findAvisPatientEnAttente(depuis, PageRequest.of(0, LOT_MAX));

        int envoyes = 0;
        for (Report report : enAttente) {
            try {
                // Sans utilisateur : l'avis n'est le geste de personne. La
                // journalisation l'accepte — voir NotificationServiceImpl#logAction.
                NotifyResponseDto resultat = notificationService.notifyPatient(report.getId(), null);
                if (!"NONE".equals(resultat.channel())) {
                    envoyes++;
                } else {
                    // Ne devrait pas arriver à 8h : la reprise s'exécute dans la
                    // plage autorisée. Le signaler plutôt que le taire.
                    log.warn("Reprise : compte-rendu {} toujours hors plage d'appel", report.getId());
                }
            } catch (RuntimeException e) {
                log.error("Reprise : avis du compte-rendu {} non envoyé : {}",
                        report.getId(), e.getMessage());
            }
        }
        return envoyes;
    }
}

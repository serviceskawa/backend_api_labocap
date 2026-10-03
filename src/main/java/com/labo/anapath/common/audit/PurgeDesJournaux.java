package com.labo.anapath.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Conservation des journaux (lot 6) : douze mois, puis purge mensuelle.
 *
 * <p>Douze mois couvrent un exercice et le délai raisonnable d'une
 * réclamation ; au-delà, garder des traces nominatives de consultation serait
 * une rétention sans finalité.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PurgeDesJournaux {

    public static final int RETENTION_MOIS = 12;

    private final JournalAccesRepository journalAcces;

    /** Le 1er de chaque mois à 3 h 30, heure du laboratoire. */
    @Scheduled(cron = "0 30 3 1 * *", zone = "Africa/Porto-Novo")
    @Transactional
    public void purger() {
        LocalDateTime avant = LocalDateTime.now().minusMonths(RETENTION_MOIS);
        Object[] compte = journalAcces.purger(avant);
        Object[] ligne = compte.length == 1 && compte[0] instanceof Object[] ? (Object[]) compte[0] : compte;
        long acces = ((Number) ligne[0]).longValue();
        long actions = ((Number) ligne[1]).longValue();
        log.info("Purge des journaux de plus de {} mois : {} consultations, {} actions sur comptes rendus",
                RETENTION_MOIS, acces, actions);
    }
}

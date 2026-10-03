package com.labo.anapath.common.audit;

import com.labo.anapath.report.LogReportRepository;
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
    private final LogReportRepository logReports;

    /** Le 1er de chaque mois à 3 h 30, heure du laboratoire. */
    @Scheduled(cron = "0 30 3 1 * *", zone = "Africa/Porto-Novo")
    @Transactional
    public void purger() {
        LocalDateTime avant = LocalDateTime.now().minusMonths(RETENTION_MOIS);
        long acces = journalAcces.deleteByAtBefore(avant);
        int actions = logReports.purgerAvant(avant);
        log.info("Purge des journaux de plus de {} mois : {} consultations, {} actions sur comptes rendus",
                RETENTION_MOIS, acces, actions);
    }
}

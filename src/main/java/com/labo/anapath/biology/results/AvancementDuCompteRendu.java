package com.labo.anapath.biology.results;

import com.labo.anapath.common.security.UserPrincipal;
import com.labo.anapath.report.LogReport;
import com.labo.anapath.report.LogReportRepository;
import com.labo.anapath.report.Report;
import com.labo.anapath.report.ReportRepository;
import com.labo.anapath.report.ReportStatus;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Fait suivre au compte-rendu d'un bon de biologie l'état de ses analyses, et tient
 * le journal ({@code log_reports}) de la saisie.
 *
 * <h2>Transitions</h2>
 * Le bon est <b>prêt</b> quand il a au moins une analyse et que toutes le sont :
 * <ul>
 *   <li>{@code TWO_STEP} : validée techniquement ;</li>
 *   <li>{@code ONE_STEP} : saisie ou validée techniquement.</li>
 * </ul>
 * Prêt et compte-rendu {@code DRAFT} → {@code PENDING_REVIEW} ; plus prêt (valeur
 * dévalidée, analyse ajoutée au bon) et {@code PENDING_REVIEW} → {@code DRAFT}. Les
 * états {@code VALIDATED} et {@code DELIVERED} relèvent de la validation biologique
 * et ne sont jamais touchés ici.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AvancementDuCompteRendu {

    static final String ACTION_PRET = "Prêt pour validation biologique";
    static final String ACTION_RETOUR = "Retour en saisie des résultats";

    private final BiologyAnalysisResultRepository analysisResultRepository;
    private final ReportRepository reportRepository;
    private final LogReportRepository logReportRepository;
    private final UserRepository userRepository;
    private final ReglagesDeBiologie reglages;

    /**
     * @param statuts états des analyses actives du bon
     * @param mode    circuit de validation
     * @return {@code true} si le compte-rendu peut passer en relecture
     */
    public static boolean pret(Collection<BiologyAnalysisStatus> statuts, BiologyValidationMode mode) {
        if (statuts == null || statuts.isEmpty()) {
            return false;
        }
        return statuts.stream().allMatch(s -> s == BiologyAnalysisStatus.TECH_VALIDATED
                || (mode == BiologyValidationMode.ONE_STEP && s == BiologyAnalysisStatus.ENTERED));
    }

    /**
     * Recalcule l'état du compte-rendu d'un bon d'après ses analyses.
     *
     * @param testOrderId bon de biologie
     * @param branchId    succursale (réglage du mode)
     * @param userId      auteur, pour le journal ; {@code null} = utilisateur connecté, s'il y en a un
     */
    public void recalculer(UUID testOrderId, UUID branchId, UUID userId) {
        Report report = reportRepository.findByTestOrderId(testOrderId).orElse(null);
        if (report == null) {
            return;
        }
        List<BiologyAnalysisStatus> statuts = analysisResultRepository.findByTestOrderId(testOrderId).stream()
                .map(BiologyAnalysisResult::getStatus).toList();
        boolean pret = pret(statuts, reglages.mode(branchId));
        if (pret && report.getStatus() == ReportStatus.DRAFT) {
            report.setStatus(ReportStatus.PENDING_REVIEW);
            reportRepository.save(report);
            journaliser(report, userId, ACTION_PRET, "Toutes les analyses du bon sont prêtes.");
            log.info("Compte-rendu {} prêt pour validation biologique", report.getId());
        } else if (!pret && report.getStatus() == ReportStatus.PENDING_REVIEW) {
            report.setStatus(ReportStatus.DRAFT);
            reportRepository.save(report);
            journaliser(report, userId, ACTION_RETOUR, "Une analyse du bon n'est plus prête.");
            log.info("Compte-rendu {} repassé en saisie", report.getId());
        }
    }

    /**
     * Écrit une entrée du journal du compte-rendu, comme le circuit d'anatomie
     * pathologique. Sans auteur connu (la colonne est obligatoire), rien n'est écrit.
     */
    public void journaliser(Report report, UUID userId, String action, String description) {
        UUID auteur = userId != null ? userId : utilisateurConnecte();
        if (report == null || auteur == null) {
            return;
        }
        userRepository.findById(auteur).ifPresent(user -> {
            LogReport entree = new LogReport();
            entree.setBranchId(report.getBranchId());
            entree.setReport(report);
            entree.setUser(user);
            entree.setAction(action);
            entree.setDescription(description);
            logReportRepository.save(entree);
        });
    }

    private static UUID utilisateurConnecte() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof UserPrincipal p ? p.getId() : null;
    }
}

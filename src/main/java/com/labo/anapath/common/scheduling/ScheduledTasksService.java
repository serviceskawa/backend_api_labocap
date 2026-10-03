package com.labo.anapath.common.scheduling;

import com.labo.anapath.branch.Branch;
import com.labo.anapath.branch.BranchRepository;
import com.labo.anapath.common.email.EmailService;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import com.labo.anapath.testorder.ReportNonFaitProjection;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.testorder.TestOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Tâches planifiées (réplique du scheduler Laravel).
 * <ul>
 *   <li>Alerte "macro non faite" — quotidienne (Laravel : {@code ->daily()}).</li>
 *   <li>Sauvegarde de la base — voir {@link SauvegardeService}.</li>
 * </ul>
 * Les méthodes {@code run*} sont publiques pour permettre un déclenchement manuel
 * (équivalent des commandes artisan), via {@code AdminTaskController}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasksService {

    private final BranchRepository branchRepository;
    private final TestOrderRepository testOrderRepository;
    private final SettingAppRepository settingAppRepository;
    private final EmailService emailService;

    /** Seuil (jours) au-delà duquel une macro non faite déclenche l'alerte. */
    @Value("${app.alerts.macro.days:7}")
    private int macroDays;

    /** Plafond d'emails d'alerte par exécution et par branche (garde-fou anti-flood). */
    @Value("${app.alerts.macro.max-per-run:200}")
    private int macroMaxPerRun;

    /** Seuil (jours) au-delà duquel un compte-rendu non fait déclenche l'alerte. */
    @Value("${app.alerts.report.days:18}")
    private int reportDays;

    /** Plafond d'emails "compte-rendu non fait" par exécution et par branche. */
    @Value("${app.alerts.report.max-per-run:200}")
    private int reportMaxPerRun;

    // ------------------------------------------------------------------
    // Alerte macro non faite — tous les jours à minuit
    // ------------------------------------------------------------------

    @Scheduled(cron = "0 0 0 * * *", zone = "Africa/Porto-Novo")
    public void macroNonFaitAlertScheduled() {
        log.info("[scheduler] Démarrage de l'alerte macro non faite");
        int sent = runMacroNonFaitAlert();
        log.info("[scheduler] Alerte macro terminée — {} email(s) déclenché(s)", sent);
    }

    /**
     * Envoie les alertes "macro non faite" pour toutes les branches.
     * Destinataires : la liste d'emails de la SettingApp {@code email_technician}
     * (séparés par {@code ;}).
     *
     * @return nombre total d'emails déclenchés
     */
    public int runMacroNonFaitAlert() {
        int totalSent = 0;
        for (Branch branch : branchRepository.findAll()) {
            String emailsRaw = settingAppRepository
                    .findByKeyAndBranchId("email_technician", branch.getId())
                    .or(() -> settingAppRepository.findByKey("email_technician"))
                    .map(SettingApp::getValue)
                    .orElse("");
            List<String> recipients = parseEmails(emailsRaw);
            if (recipients.isEmpty()) {
                continue; // aucune adresse configurée pour cette branche
            }

            String labName = settingAppRepository.findByKey("lab_name")
                    .map(SettingApp::getValue)
                    .orElse("le laboratoire");

            List<TestOrder> overdue =
                    // La macroscopie n'existe qu'en anatomie pathologique.
                    testOrderRepository.findOverdueWithoutMacro(branch.getId(), macroDays, macroMaxPerRun,
                            com.labo.anapath.common.Discipline.PATHOLOGY.name());

            for (TestOrder order : overdue) {
                for (String to : recipients) {
                    emailService.sendMacroAlert(to, order.getCode(), labName);
                    totalSent++;
                }
            }
            log.info("Alerte macro — branche {} : {} demande(s) en retard (plafond {}), {} destinataire(s)",
                    branch.getId(), overdue.size(), macroMaxPerRun, recipients.size());
        }
        return totalSent;
    }

    // ------------------------------------------------------------------
    // Alerte compte-rendu non fait — tous les jours à minuit
    // ------------------------------------------------------------------

    @Scheduled(cron = "0 0 0 * * *", zone = "Africa/Porto-Novo")
    public void reportNonFaitAlertScheduled() {
        log.info("[scheduler] Démarrage de l'alerte compte-rendu non fait");
        int sent = runReportNonFaitAlert();
        log.info("[scheduler] Alerte compte-rendu terminée — {} email(s) déclenché(s)", sent);
    }

    /**
     * Envoie les alertes "compte-rendu non fait" pour toutes les branches.
     * Destinataire : le pathologiste assigné à la demande ({@code attribuate_doctor_id}),
     * pour tout bon créé il y a plus de {@code reportDays} jours sans compte-rendu validé.
     *
     * @return nombre total d'emails déclenchés
     */
    public int runReportNonFaitAlert() {
        int totalSent = 0;
        for (Branch branch : branchRepository.findAll()) {
            String labName = settingAppRepository.findByKey("lab_name")
                    .map(SettingApp::getValue)
                    .orElse("le laboratoire");

            List<ReportNonFaitProjection> overdue =
                    // Alerte adressée au pathologiste : la biologie aura son propre circuit.
                    testOrderRepository.findOverdueWithoutReport(branch.getId(), reportDays, reportMaxPerRun,
                            com.labo.anapath.common.Discipline.PATHOLOGY.name());

            for (ReportNonFaitProjection row : overdue) {
                if (row.getEmail() == null || row.getEmail().isBlank()) {
                    continue;
                }
                emailService.sendReportNonFaitAlert(
                        row.getEmail(), row.getDoctorName(), row.getTestOrderCode(), reportDays, labName);
                totalSent++;
            }
            log.info("Alerte compte-rendu — branche {} : {} demande(s) en retard (plafond {})",
                    branch.getId(), overdue.size(), reportMaxPerRun);
        }
        return totalSent;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static List<String> parseEmails(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        for (String part : raw.split(";")) {
            String email = part.trim();
            if (!email.isEmpty() && email.contains("@")) {
                result.add(email);
            }
        }
        return result;
    }
}

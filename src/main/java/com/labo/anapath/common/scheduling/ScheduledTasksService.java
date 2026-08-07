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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Tâches planifiées (réplique du scheduler Laravel).
 * <ul>
 *   <li>Alerte "macro non faite" — quotidienne (Laravel : {@code ->daily()}).</li>
 *   <li>Sauvegarde de la base — quotidienne à 18h30 (Laravel : {@code ->dailyAt('18:30')}).</li>
 * </ul>
 * Les méthodes {@code run*} sont publiques pour permettre un déclenchement manuel
 * (équivalent des commandes artisan), via {@code AdminTaskController}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasksService {

    private static final DateTimeFormatter BACKUP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

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

    /** Répertoire de destination des sauvegardes. */
    @Value("${app.backup.dir:backups}")
    private String backupDir;

    /**
     * Commande de sauvegarde (sortie SQL sur stdout).
     *
     * <p>La valeur par défaut était {@code docker exec labo_postgres pg_dump …},
     * héritée d'une exécution depuis l'hôte. Dans le conteneur, elle ne pouvait
     * pas aboutir : l'image ne contient pas de client Docker et la socket n'est
     * pas montée. La commande échouait chaque soir, l'exception était attrapée,
     * une ligne de journal écrite — et aucune sauvegarde n'a jamais existé.</p>
     *
     * <p>Elle s'adresse désormais au serveur par le réseau, comme l'application
     * elle-même. Le client PostgreSQL est installé dans l'image (cf. Dockerfile),
     * à la même version majeure que le serveur : un {@code pg_dump} plus ancien
     * refuse de sauvegarder une base plus récente.</p>
     */
    @Value("${app.backup.command:pg_dump -h db -p 5432 -U postgres -d labo_anapath}")
    private String backupCommand;

    /**
     * Mot de passe transmis à {@code pg_dump} par la variable {@code PGPASSWORD}.
     *
     * <p>Passé par l'environnement du processus fils et non sur la ligne de
     * commande : les arguments d'un processus sont lisibles par tout utilisateur
     * de la machine ({@code /proc/<pid>/cmdline}).</p>
     */
    @Value("${app.backup.password:}")
    private String backupPassword;

    /**
     * Nombre de sauvegardes conservées. Au-delà, les plus anciennes sont
     * effacées après chaque exécution réussie.
     *
     * <p>Rien ne les purgeait : un dump pèse une soixantaine de méga-octets, ce
     * qui remplit le disque en une année — et chacun porte l'intégralité du
     * dossier clinique.</p>
     */
    @Value("${app.backup.keep:30}")
    private int backupKeep;

    // ------------------------------------------------------------------
    // Alerte macro non faite — tous les jours à minuit
    // ------------------------------------------------------------------

    @Scheduled(cron = "0 0 0 * * *")
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
                    testOrderRepository.findOverdueWithoutMacro(branch.getId(), macroDays, macroMaxPerRun);

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

    @Scheduled(cron = "0 0 0 * * *")
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
                    testOrderRepository.findOverdueWithoutReport(branch.getId(), reportDays, reportMaxPerRun);

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
    // Sauvegarde de la base — tous les jours à 18h30
    // ------------------------------------------------------------------

    @Scheduled(cron = "0 30 18 * * *")
    public void databaseBackupScheduled() {
        log.info("[scheduler] Démarrage de la sauvegarde quotidienne de la base");
        runDatabaseBackup();
    }

    /**
     * Exécute la commande de sauvegarde et écrit le dump dans {@code backupDir}.
     *
     * @return le chemin du fichier créé, ou {@code null} en cas d'échec
     */
    public String runDatabaseBackup() {
        try {
            Path dir = Path.of(backupDir);
            Files.createDirectories(dir);
            String filename = "backup-" + LocalDateTime.now().format(BACKUP_FMT) + ".sql";
            File target = dir.resolve(filename).toFile();

            ProcessBuilder pb = new ProcessBuilder(splitCommand(backupCommand));
            if (backupPassword != null && !backupPassword.isBlank()) {
                pb.environment().put("PGPASSWORD", backupPassword);
            }
            pb.redirectOutput(target);
            // Les erreurs de pg_dump doivent apparaître dans le journal de
            // l'application ; héritées de la sortie standard, elles se perdaient
            // là où personne ne les lit.
            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = pb.start();

            boolean finished = process.waitFor(10, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Délai dépassé pour la commande de sauvegarde");
            }
            int exit = process.exitValue();
            if (exit != 0) {
                throw new IllegalStateException("La commande de sauvegarde a échoué (code " + exit + ")");
            }

            long size = target.length();
            if (size == 0) {
                throw new IllegalStateException("Le fichier de sauvegarde est vide");
            }
            log.info("Sauvegarde de la base créée : {} ({} octets)", target.getAbsolutePath(), size);
            purgerAnciennesSauvegardes(dir);
            return target.getAbsolutePath();
        } catch (Exception e) {
            log.error("Échec de la sauvegarde de la base : {}", e.getMessage());
            return null;
        }
    }

    /**
     * Ne conserve que les {@code backupKeep} sauvegardes les plus récentes.
     *
     * <p>Appelée après une sauvegarde RÉUSSIE seulement : purger alors qu'on
     * vient d'échouer reviendrait à effacer les seules copies valides qui
     * restent.</p>
     */
    private void purgerAnciennesSauvegardes(Path dir) {
        if (backupKeep <= 0) return;
        try (var flux = Files.list(dir)) {
            List<Path> dumps = flux
                    .filter(p -> p.getFileName().toString().startsWith("backup-"))
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    // Le nom porte l'horodatage : l'ordre alphabétique est
                    // l'ordre chronologique, sans lire le système de fichiers.
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path vieux : dumps.stream().skip(backupKeep).toList()) {
                try {
                    Files.deleteIfExists(vieux);
                    log.info("Sauvegarde ancienne effacée : {}", vieux.getFileName());
                } catch (Exception e) {
                    log.warn("Effacement impossible ({}) : {}", vieux.getFileName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Purge des sauvegardes impossible : {}", e.getMessage());
        }
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

    private static String[] splitCommand(String command) {
        return Arrays.stream(command.trim().split("\\s+"))
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }
}

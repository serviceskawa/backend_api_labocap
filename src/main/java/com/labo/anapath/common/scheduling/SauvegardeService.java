package com.labo.anapath.common.scheduling;

import com.labo.anapath.common.email.EmailService;
import com.labo.anapath.common.email.NotificationSettings;
import com.labo.anapath.common.storage.DepotS3;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Sauvegarde quotidienne de la base et des fichiers, hors du serveur.
 *
 * <h2>Ce que corrige cette classe</h2>
 *
 * <p>Jusqu'ici, {@code pg_dump} écrivait chaque soir un fichier en clair dans un
 * volume du même serveur, et un échec ne produisait qu'une ligne de journal
 * que personne ne lisait. Un disque qui meurt, un serveur compromis, un volume
 * emporté : dans les trois cas, la sauvegarde partait avec le reste, ou s'y
 * lisait comme un livre ouvert.</p>
 *
 * <h2>Trois temps, chaque soir</h2>
 *
 * <ol>
 *   <li><b>18h30</b> — l'export est chiffré à la volée avec {@code age}, contre une
 *       clé publique. La clé privée n'a jamais existé sur le serveur : qui
 *       l'emporte n'emporte rien de lisible. Le fichier chiffré part ensuite
 *       dans un seau S3 réservé aux sauvegardes, puis le volume {@code storage}
 *       est synchronisé vers le même seau.</li>
 *   <li><b>20h</b> — un contrôle indépendant vérifie qu'un objet du jour existe
 *       dans le seau. Il ne partage rien avec la sauvegarde elle-même : si
 *       celle-ci s'est éteinte sans un mot, lui le dira.</li>
 *   <li>Tout échec — export en erreur ou vide, copie refusée, contrôle
 *       négatif — part par courriel aux administrateurs ({@code admin_mails})
 *       et au prestataire ({@code BACKUP_ALERT_EMAIL}).</li>
 * </ol>
 *
 * <p>Les méthodes publiques sont appelables hors planification, pour un
 * déclenchement manuel ou un test.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SauvegardeService {

    /** Heure du laboratoire : c'est elle qui date les fichiers et les contrôles. */
    static final ZoneId FUSEAU = ZoneId.of("Africa/Porto-Novo");

    private static final DateTimeFormatter HORODATAGE = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    /** Préfixes, dans le seau, des exports de base et de la copie des fichiers. */
    static final String PREFIXE_BASE = "base/";
    static final String PREFIXE_STOCKAGE = "storage/";

    /** Jeton remplacé par la clé publique dans la commande de chiffrement. */
    private static final String JETON_DESTINATAIRE = "{destinataire}";

    private final SettingAppRepository settingAppRepository;
    private final EmailService emailService;
    private final Environment environment;

    /** Répertoire local des exports — un volume, pour survivre au redéploiement. */
    @Value("${app.backup.dir:backups}")
    private String backupDir;

    /**
     * Commande d'export (SQL sur la sortie standard). Elle s'adresse au serveur
     * par le réseau, comme l'application ; le client PostgreSQL est dans l'image.
     */
    @Value("${app.backup.command:pg_dump -h db -p 5432 -U postgres -d labo_anapath}")
    private String backupCommand;

    /**
     * Commande de chiffrement, qui lit l'export sur son entrée standard et
     * écrit le fichier chiffré sur sa sortie. {@value #JETON_DESTINATAIRE} y est
     * remplacé par la clé publique. Un test la remplace par {@code cat}.
     */
    @Value("${app.backup.encrypt-command:age -r {destinataire}}")
    private String encryptCommand;

    /**
     * Clé publique {@code age} du destinataire des sauvegardes. La clé privée
     * correspondante est conservée hors du serveur — c'est tout l'intérêt.
     */
    @Value("${app.backup.age-recipient:}")
    private String ageRecipient;

    /**
     * Mot de passe transmis à {@code pg_dump} par la variable {@code PGPASSWORD}.
     * Par l'environnement et non sur la ligne de commande : les arguments d'un
     * processus sont lisibles par tout utilisateur de la machine.
     */
    @Value("${app.backup.password:}")
    private String backupPassword;

    /** Nombre d'exports conservés localement. Au-delà, les plus anciens sont effacés. */
    @Value("${app.backup.keep:30}")
    private int backupKeep;

    /** Adresse du prestataire, prévenue en plus des administrateurs. */
    @Value("${app.backup.alert-email:}")
    private String alertEmail;

    /** Racine des fichiers locaux à copier sous {@value #PREFIXE_STOCKAGE}. */
    @Value("${app.storage.path:}")
    private String storagePath;

    @Value("${app.backup.s3.bucket:}")
    private String s3Bucket;
    @Value("${app.backup.s3.region:eu-west-3}")
    private String s3Region;
    @Value("${app.backup.s3.access-key:}")
    private String s3AccessKey;
    @Value("${app.backup.s3.secret-key:}")
    private String s3SecretKey;
    @Value("${app.backup.s3.endpoint:}")
    private String s3Endpoint;

    /**
     * Client du seau de sauvegardes, ou {@code null} si aucun seau n'est
     * configuré. Distinct de celui des fichiers : autre seau, autres clés — une
     * clé en écriture seule, qui ne sait pas effacer ce qu'elle a déposé.
     */
    private S3Client s3;

    @PostConstruct
    void construireLeClientS3() {
        if (s3Bucket == null || s3Bucket.isBlank()) {
            log.warn("Aucun seau de sauvegarde (BACKUP_S3_BUCKET vide) : les exports resteront sur ce serveur.");
            return;
        }
        s3 = DepotS3.construireClient(s3Region, s3AccessKey, s3SecretKey, s3Endpoint);
        log.info("Copie externe des sauvegardes active : seau={} région={}", s3Bucket, s3Region);
    }

    // ------------------------------------------------------------------
    // 18h30 — export chiffré, copie externe, synchronisation des fichiers
    // ------------------------------------------------------------------

    @Scheduled(cron = "0 30 18 * * *", zone = "Africa/Porto-Novo")
    public void sauvegardeQuotidienne() {
        log.info("[scheduler] Démarrage de la sauvegarde quotidienne");
        sauvegarderLaBase();
        synchroniserLeStockage();
    }

    /**
     * Exporte la base, chiffrée, dans {@code backupDir}, puis la copie dans le seau.
     *
     * @return le chemin du fichier créé, ou {@code null} si rien n'a été produit
     */
    public Path sauvegarderLaBase() {
        boolean chiffree = ageRecipient != null && !ageRecipient.isBlank();
        if (!chiffree && enProduction()) {
            // Mieux vaut pas de sauvegarde ce soir, et quelqu'un prévenu, qu'un
            // dossier clinique entier en clair sur un disque qu'on ne maîtrise pas.
            alerter("Sauvegarde refusée : aucune clé de chiffrement",
                    "BACKUP_AGE_RECIPIENT est vide. En production, la base n'est pas "
                    + "sauvegardée en clair. Renseigner la clé publique age dans le .env "
                    + "du serveur et redémarrer l'API.");
            return null;
        }
        if (!chiffree) {
            log.warn("BACKUP_AGE_RECIPIENT vide : sauvegarde EN CLAIR (toléré hors production).");
        }

        Path cible;
        try {
            Path dir = Path.of(backupDir);
            Files.createDirectories(dir);
            String nom = "backup-" + LocalDateTime.now(FUSEAU).format(HORODATAGE) + (chiffree ? ".sql.age" : ".sql");
            cible = dir.resolve(nom);
            long octets = exporter(cible, chiffree);
            if (octets == 0) {
                Files.deleteIfExists(cible);
                throw new IllegalStateException("l'export est vide (0 octet produit par la commande de sauvegarde)");
            }
            log.info("Export de la base créé : {} ({} octets avant chiffrement)", cible, octets);
        } catch (Exception e) {
            alerter("Échec de l'export de la base", "La sauvegarde de ce soir n'a pas été produite : "
                    + e.getMessage() + "\nVoir le journal de l'API (docker logs labo-api).");
            return null;
        }

        boolean copie = copierDansLeSeau(PREFIXE_BASE + cible.getFileName(), cible);
        // Purge seulement après une copie réussie : effacer les anciens exports
        // alors que celui du jour n'est pas parti hors du serveur reviendrait à
        // réduire les seules copies qui existent. Sans seau du tout, le disque
        // local est la seule copie et doit quand même être tenu.
        if (copie || s3 == null) {
            purgerAnciennesSauvegardes(cible.getParent());
        }
        return cible;
    }

    /**
     * Enchaîne la commande d'export et celle de chiffrement, sans jamais poser
     * le SQL en clair sur le disque, et rend le nombre d'octets exportés.
     *
     * <p>Le transfert passe par ce processus plutôt que par
     * {@code ProcessBuilder.startPipeline} : c'est ce qui permet de compter ce
     * que {@code pg_dump} a réellement produit. Un fichier chiffré n'est jamais
     * vide — {@code age} écrit un en-tête même sans entrée — et la taille de
     * sortie ne dirait donc rien d'un export vide.</p>
     */
    private long exporter(Path cible, boolean chiffree) throws Exception {
        ProcessBuilder export = new ProcessBuilder(decouper(backupCommand));
        if (backupPassword != null && !backupPassword.isBlank()) {
            export.environment().put("PGPASSWORD", backupPassword);
        }
        // Les erreurs des commandes vont dans le journal de l'application ;
        // perdues dans un tuyau, personne ne les lirait.
        export.redirectError(ProcessBuilder.Redirect.INHERIT);

        ProcessBuilder sortie = chiffree
                ? new ProcessBuilder(decouper(encryptCommand.replace(JETON_DESTINATAIRE, ageRecipient.trim())))
                : new ProcessBuilder("cat");
        sortie.redirectError(ProcessBuilder.Redirect.INHERIT);
        sortie.redirectOutput(cible.toFile());

        Process dump = export.start();
        Process chiffrement = sortie.start();
        CompletableFuture<Long> transfert = CompletableFuture.supplyAsync(() -> {
            try (var in = dump.getInputStream(); var out = chiffrement.getOutputStream()) {
                return in.transferTo(out);
            } catch (IOException e) {
                throw new IllegalStateException("transfert vers la commande de chiffrement interrompu", e);
            }
        });
        try {
            // Le planificateur de Spring n'a qu'un fil : un pg_dump figé y
            // bloquerait aussi les alertes et la purge des jetons.
            long octets = transfert.get(10, TimeUnit.MINUTES);
            if (!dump.waitFor(1, TimeUnit.MINUTES) || !chiffrement.waitFor(1, TimeUnit.MINUTES)) {
                throw new TimeoutException();
            }
            if (dump.exitValue() != 0) {
                throw new IllegalStateException("la commande de sauvegarde a échoué (code " + dump.exitValue() + ")");
            }
            if (chiffrement.exitValue() != 0) {
                throw new IllegalStateException("la commande de chiffrement a échoué (code " + chiffrement.exitValue() + ")");
            }
            return octets;
        } catch (TimeoutException e) {
            dump.destroyForcibly();
            chiffrement.destroyForcibly();
            throw new IllegalStateException("délai dépassé pour la commande de sauvegarde");
        }
    }

    /**
     * Ne conserve que les {@code backupKeep} exports locaux les plus récents.
     * Le nom porte l'horodatage : l'ordre alphabétique est l'ordre chronologique.
     */
    private void purgerAnciennesSauvegardes(Path dir) {
        if (backupKeep <= 0) return;
        try (Stream<Path> flux = Files.list(dir)) {
            List<Path> exports = flux
                    .filter(p -> p.getFileName().toString().startsWith("backup-"))
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path vieux : exports.stream().skip(backupKeep).toList()) {
                try {
                    Files.deleteIfExists(vieux);
                    log.info("Export ancien effacé : {}", vieux.getFileName());
                } catch (Exception e) {
                    log.warn("Effacement impossible ({}) : {}", vieux.getFileName(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Purge des exports impossible : {}", e.getMessage());
        }
    }

    /**
     * Envoie vers le seau, sous {@value #PREFIXE_STOCKAGE}, les fichiers locaux
     * nouveaux ou modifiés depuis leur dernier envoi.
     *
     * <p>L'état de référence est l'inventaire du seau lui-même (une requête par
     * millier d'objets), et non un index local : un serveur réinstallé repart
     * ainsi du bon pied sans rien réenvoyer d'inutile. Un fichier est renvoyé
     * si sa taille diffère ou s'il a été modifié après la date de son objet.</p>
     *
     * <p>Les octets partent tels quels : chiffrés au repos quand
     * {@code STORAGE_ENCRYPTION_ENABLED} l'est, en clair sinon.</p>
     *
     * @return le nombre de fichiers envoyés, ou -1 si rien n'a été tenté
     */
    public int synchroniserLeStockage() {
        if (s3 == null || storagePath == null || storagePath.isBlank() || !Files.isDirectory(Path.of(storagePath))) {
            return -1;
        }
        Path racine = Path.of(storagePath);
        int envoyes = 0;
        int echecs = 0;
        try {
            Map<String, S3Object> distants = inventaire(PREFIXE_STOCKAGE);
            try (Stream<Path> flux = Files.walk(racine)) {
                for (Path fichier : flux.filter(Files::isRegularFile).toList()) {
                    String cle = PREFIXE_STOCKAGE + racine.relativize(fichier).toString();
                    S3Object distant = distants.get(cle);
                    long taille = Files.size(fichier);
                    var modifie = Files.getLastModifiedTime(fichier).toInstant();
                    boolean aJour = distant != null && distant.size() == taille
                            && !modifie.isAfter(distant.lastModified());
                    if (aJour) continue;
                    if (copierDansLeSeau(cle, fichier, false)) envoyes++;
                    else echecs++;
                }
            }
        } catch (Exception e) {
            alerter("Échec de la copie des fichiers", "La synchronisation du volume storage vers le seau "
                    + s3Bucket + " s'est interrompue : " + e.getMessage());
            return envoyes;
        }
        log.info("Fichiers synchronisés vers {}/{} : {} envoyé(s), {} en échec", s3Bucket, PREFIXE_STOCKAGE, envoyes, echecs);
        if (echecs > 0) {
            alerter("Échec de la copie des fichiers", echecs + " fichier(s) du volume storage n'ont pas pu "
                    + "être copiés dans le seau " + s3Bucket + ". Détail dans le journal de l'API.");
        }
        return envoyes;
    }

    // ------------------------------------------------------------------
    // 20h — contrôle indépendant de la copie du jour
    // ------------------------------------------------------------------

    @Scheduled(cron = "0 0 20 * * *", zone = "Africa/Porto-Novo")
    public void controleQuotidien() {
        verifierLaCopieDuJour();
    }

    /**
     * Vérifie qu'un export daté d'aujourd'hui existe dans le seau.
     *
     * @return vrai si l'objet du jour est là
     */
    public boolean verifierLaCopieDuJour() {
        if (s3 == null) {
            if (enProduction()) {
                alerter("Aucun seau de sauvegarde configuré", "BACKUP_S3_BUCKET est vide : aucune copie "
                        + "de la base n'existe hors de ce serveur. Renseigner les variables BACKUP_S3_* "
                        + "du .env et redémarrer l'API.");
            }
            return false;
        }
        String prefixe = PREFIXE_BASE + "backup-" + LocalDate.now(FUSEAU);
        try {
            boolean presente = !inventaire(prefixe).isEmpty();
            if (!presente) {
                alerter("Aucune sauvegarde du jour dans le seau", "À 20h, aucun objet « " + prefixe
                        + "* » n'existe dans le seau " + s3Bucket + ". L'export de 18h30 a échoué ou "
                        + "n'est pas parti. Voir le journal de l'API, puis relancer la sauvegarde.");
            }
            return presente;
        } catch (Exception e) {
            alerter("Contrôle de la sauvegarde impossible", "Le seau " + s3Bucket
                    + " n'a pas pu être interrogé : " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Seau et alertes
    // ------------------------------------------------------------------

    private boolean copierDansLeSeau(String cle, Path fichier) {
        return copierDansLeSeau(cle, fichier, true);
    }

    /**
     * @param alerterSiEchec faux pour la synchronisation des fichiers, qui
     *                       regroupe ses échecs en un seul message
     */
    private boolean copierDansLeSeau(String cle, Path fichier, boolean alerterSiEchec) {
        if (s3 == null) {
            if (alerterSiEchec && enProduction()) {
                alerter("Sauvegarde restée sur le serveur", "BACKUP_S3_BUCKET est vide : l'export "
                        + fichier.getFileName() + " n'a pas de copie hors du serveur.");
            }
            return false;
        }
        try {
            s3.putObject(PutObjectRequest.builder().bucket(s3Bucket).key(cle).build(),
                    RequestBody.fromFile(fichier));
            log.info("Copié dans le seau {} : {}", s3Bucket, cle);
            return true;
        } catch (Exception e) {
            log.error("Copie dans le seau {} impossible ({}) : {}", s3Bucket, cle, e.getMessage());
            if (alerterSiEchec) {
                alerter("Échec de la copie externe", "L'export " + fichier.getFileName()
                        + " existe sur le serveur mais n'a pas pu être copié dans le seau "
                        + s3Bucket + " : " + e.getMessage());
            }
            return false;
        }
    }

    /** Les objets du seau sous un préfixe, par clé. */
    private Map<String, S3Object> inventaire(String prefixe) {
        Map<String, S3Object> objets = new HashMap<>();
        String suite = null;
        do {
            ListObjectsV2Response page = s3.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(s3Bucket).prefix(prefixe).continuationToken(suite).build());
            for (S3Object o : page.contents()) objets.put(o.key(), o);
            suite = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
        } while (suite != null);
        return objets;
    }

    /**
     * Prévient les administrateurs ({@code admin_mails}, toutes branches) et le
     * prestataire. Sans aucune adresse, le journal est la seule trace — en
     * erreur, pour qu'elle saute aux yeux.
     */
    private void alerter(String sujet, String detail) {
        log.error("[sauvegarde] {} — {}", sujet, detail);
        Set<String> destinataires = new LinkedHashSet<>();
        for (SettingApp reglage : settingAppRepository.findByKeyInOrderByCreatedAtAsc(List.of("admin_mails"))) {
            destinataires.addAll(NotificationSettings.parseEmails(reglage.getValue()));
        }
        destinataires.addAll(NotificationSettings.parseEmails(alertEmail));
        if (destinataires.isEmpty()) {
            log.error("[sauvegarde] Aucune adresse d'alerte (admin_mails et BACKUP_ALERT_EMAIL vides).");
        }
        for (String to : destinataires) {
            emailService.sendAlerteSauvegarde(to, sujet, detail);
        }
    }

    private boolean enProduction() {
        return environment.acceptsProfiles(Profiles.of("prod"));
    }

    private static List<String> decouper(String commande) {
        return new ArrayList<>(Arrays.stream(commande.trim().split("\\s+")).filter(s -> !s.isEmpty()).toList());
    }
}

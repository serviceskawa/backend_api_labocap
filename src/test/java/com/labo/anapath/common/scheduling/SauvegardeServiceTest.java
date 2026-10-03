package com.labo.anapath.common.scheduling;

import com.labo.anapath.common.email.EmailService;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Les commandes externes sont remplacées par des utilitaires toujours
 * présents : {@code printf}/{@code true}/{@code false} pour {@code pg_dump},
 * {@code cat} pour {@code age}. Le seau est un client S3 simulé.
 */
@ExtendWith(MockitoExtension.class)
class SauvegardeServiceTest {

    @Mock SettingAppRepository settingAppRepository;
    @Mock EmailService emailService;
    @Mock S3Client s3;

    @TempDir Path tmp;

    MockEnvironment env = new MockEnvironment();
    SauvegardeService service;

    @BeforeEach
    void setup() {
        service = new SauvegardeService(settingAppRepository, emailService, env);
        ReflectionTestUtils.setField(service, "backupDir", tmp.resolve("backups").toString());
        ReflectionTestUtils.setField(service, "backupCommand", "printf abc");
        ReflectionTestUtils.setField(service, "encryptCommand", "cat");
        ReflectionTestUtils.setField(service, "ageRecipient", "age1test");
        ReflectionTestUtils.setField(service, "backupPassword", "");
        ReflectionTestUtils.setField(service, "backupKeep", 30);
        ReflectionTestUtils.setField(service, "alertEmail", "presta@exemple.bj");
        ReflectionTestUtils.setField(service, "storagePath", tmp.resolve("storage").toString());
        ReflectionTestUtils.setField(service, "s3Bucket", "labo-sauvegardes");
        ReflectionTestUtils.setField(service, "s3", s3);
    }

    /** Un administrateur est configuré dans {@code admin_mails}. */
    private void adminConfigure() {
        SettingApp reglage = new SettingApp();
        reglage.setKey("admin_mails");
        reglage.setValue("admin@exemple.bj");
        when(settingAppRepository.findByKeyInOrderByCreatedAtAsc(any())).thenReturn(List.of(reglage));
    }

    private void seauVide() {
        when(s3.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenReturn(ListObjectsV2Response.builder().isTruncated(false).build());
    }

    @Test
    @DisplayName("Un export réussi est chiffré, écrit localement et envoyé dans le seau")
    void exportReussi_envoyeDansLeSeau() throws Exception {
        Path fichier = service.sauvegarderLaBase();

        assertThat(fichier).isNotNull();
        assertThat(fichier.getFileName().toString()).startsWith("backup-").endsWith(".sql.age");
        assertThat(Files.readString(fichier)).isEqualTo("abc");
        verify(s3).putObject(argThat((PutObjectRequest r) ->
                r.bucket().equals("labo-sauvegardes")
                        && r.key().equals("base/" + fichier.getFileName())), any(RequestBody.class));
        verify(emailService, never()).sendAlerteSauvegarde(any(), any(), any());
    }

    @Test
    @DisplayName("Un export vide déclenche l'alerte aux administrateurs et au prestataire, sans envoi")
    void exportVide_alerte() throws Exception {
        adminConfigure();
        ReflectionTestUtils.setField(service, "backupCommand", "true");

        assertThat(service.sauvegarderLaBase()).isNull();

        verify(emailService).sendAlerteSauvegarde(eq("admin@exemple.bj"), contains("export"), contains("vide"));
        verify(emailService).sendAlerteSauvegarde(eq("presta@exemple.bj"), contains("export"), contains("vide"));
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        try (var flux = Files.list(tmp.resolve("backups"))) {
            assertThat(flux).as("aucun fichier vide ne doit rester").isEmpty();
        }
    }

    @Test
    @DisplayName("Une commande d'export en échec déclenche l'alerte")
    void commandeEnEchec_alerte() {
        adminConfigure();
        ReflectionTestUtils.setField(service, "backupCommand", "false");

        assertThat(service.sauvegarderLaBase()).isNull();

        verify(emailService).sendAlerteSauvegarde(eq("admin@exemple.bj"), contains("export"), contains("code 1"));
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("En production sans clé publique, la sauvegarde en clair est refusée et signalée")
    void prodSansCle_refus() {
        adminConfigure();
        env.setActiveProfiles("prod");
        ReflectionTestUtils.setField(service, "ageRecipient", "");

        assertThat(service.sauvegarderLaBase()).isNull();

        verify(emailService).sendAlerteSauvegarde(eq("admin@exemple.bj"), contains("refusée"),
                contains("BACKUP_AGE_RECIPIENT"));
        verify(s3, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        assertThat(tmp.resolve("backups")).doesNotExist();
    }

    @Test
    @DisplayName("Hors production sans clé publique, la sauvegarde part en clair")
    void devSansCle_enClair() throws Exception {
        ReflectionTestUtils.setField(service, "ageRecipient", "");

        Path fichier = service.sauvegarderLaBase();

        assertThat(fichier.getFileName().toString()).endsWith(".sql");
        assertThat(Files.readString(fichier)).isEqualTo("abc");
        verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        verify(emailService, never()).sendAlerteSauvegarde(any(), any(), any());
    }

    @Test
    @DisplayName("Une copie S3 en échec déclenche l'alerte mais garde l'export local")
    void copieEnEchec_alerte() {
        adminConfigure();
        when(s3.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(new RuntimeException("accès refusé"));

        Path fichier = service.sauvegarderLaBase();

        assertThat(fichier).exists();
        verify(emailService).sendAlerteSauvegarde(eq("admin@exemple.bj"), contains("copie externe"),
                contains("accès refusé"));
    }

    @Test
    @DisplayName("Contrôle de 20h : aucun objet du jour dans le seau → alerte")
    void controle_aucunObjetDuJour_alerte() {
        adminConfigure();
        seauVide();

        assertThat(service.verifierLaCopieDuJour()).isFalse();

        verify(s3).listObjectsV2(argThat((ListObjectsV2Request r) ->
                r.prefix().equals("base/backup-" + LocalDate.now(SauvegardeService.FUSEAU))));
        verify(emailService).sendAlerteSauvegarde(eq("admin@exemple.bj"), contains("Aucune sauvegarde du jour"), any());
        verify(emailService).sendAlerteSauvegarde(eq("presta@exemple.bj"), contains("Aucune sauvegarde du jour"), any());
    }

    @Test
    @DisplayName("Contrôle de 20h : l'objet du jour existe → rien à signaler")
    void controle_objetPresent_silence() {
        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("base/backup-x.sql.age").size(1L).lastModified(Instant.now()).build())
                .isTruncated(false).build());

        assertThat(service.verifierLaCopieDuJour()).isTrue();

        verify(emailService, never()).sendAlerteSauvegarde(any(), any(), any());
    }

    @Test
    @DisplayName("Synchronisation des fichiers : seuls les fichiers nouveaux ou modifiés partent")
    void synchronisation_nEnvoieQueLeNouveauEtLeModifie() throws Exception {
        Path storage = tmp.resolve("storage");
        Files.createDirectories(storage.resolve("sous"));
        Files.writeString(storage.resolve("nouveau.bin"), "n");
        Files.writeString(storage.resolve("sous/inchange.bin"), "ii");
        Files.writeString(storage.resolve("modifie.bin"), "mmm");
        Instant hier = Instant.now().minusSeconds(86_400);
        Files.setLastModifiedTime(storage.resolve("sous/inchange.bin"), FileTime.from(hier));

        when(s3.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(ListObjectsV2Response.builder()
                .contents(
                        // même taille, envoyé après la dernière modification locale : à jour
                        S3Object.builder().key("storage/sous/inchange.bin").size(2L).lastModified(Instant.now()).build(),
                        // envoyé avant la modification locale : à renvoyer
                        S3Object.builder().key("storage/modifie.bin").size(3L).lastModified(hier).build())
                .isTruncated(false).build());

        assertThat(service.synchroniserLeStockage()).isEqualTo(2);

        verify(s3).putObject(argThat((PutObjectRequest r) -> r.key().equals("storage/nouveau.bin")), any(RequestBody.class));
        verify(s3).putObject(argThat((PutObjectRequest r) -> r.key().equals("storage/modifie.bin")), any(RequestBody.class));
        verify(s3, never()).putObject(argThat((PutObjectRequest r) -> r.key().equals("storage/sous/inchange.bin")), any(RequestBody.class));
        verify(emailService, never()).sendAlerteSauvegarde(any(), any(), any());
    }
}

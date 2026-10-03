package com.labo.anapath.common.supervision;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Jauges pour le tableau de bord que Spring Boot ne fournit pas de lui-même :
 * l'espace libre du volume de stockage et de la racine, et la date du dernier
 * dump de la base. Lues par Prometheus avec le reste de {@code /actuator/prometheus}.
 */
@Slf4j
@Component
public class JaugesDeSupervision {

    private final MeterRegistry registry;
    private final List<Path> volumes;
    private final Path dossierDesSauvegardes;

    public JaugesDeSupervision(MeterRegistry registry,
                               @Value("${app.storage.path:/tmp/labo/storage}") String storagePath,
                               @Value("${app.backup.dir:backups}") String backupDir) {
        this.registry = registry;
        this.volumes = List.of(Path.of(storagePath), Path.of("/"));
        this.dossierDesSauvegardes = Path.of(backupDir);
    }

    @PostConstruct
    void enregistrer() {
        for (Path volume : volumes) {
            Gauge.builder("labo.disque.libre.pourcent", volume, JaugesDeSupervision::pourcentLibre)
                    .tag("volume", volume.toString())
                    .description("Part de l'espace disque encore libre")
                    .register(registry);
        }
        Gauge.builder("labo.sauvegarde.derniere.epoch.secondes", dossierDesSauvegardes,
                        JaugesDeSupervision::dateDuDernierDump)
                .description("Date (epoch, secondes) du dernier dump de la base ; 0 si aucun")
                .register(registry);
    }

    /** -1 si le volume n'est pas mesurable : un chiffre faux (0 %) déclencherait une alerte à tort. */
    static double pourcentLibre(Path volume) {
        try {
            FileStore fs = Files.getFileStore(volume);
            return fs.getTotalSpace() <= 0 ? -1 : fs.getUsableSpace() * 100.0 / fs.getTotalSpace();
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    static double dateDuDernierDump(Path dossier) {
        if (!Files.isDirectory(dossier)) return 0;
        try (Stream<Path> fichiers = Files.list(dossier)) {
            return fichiers.filter(p -> p.getFileName().toString().startsWith("backup-"))
                    .mapToLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis() / 1000;
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .max().orElse(0);
        } catch (IOException e) {
            return 0;
        }
    }
}

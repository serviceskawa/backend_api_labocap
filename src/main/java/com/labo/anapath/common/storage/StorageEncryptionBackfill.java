package com.labo.anapath.common.storage;

import com.labo.anapath.common.exception.InvalidOperationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * Rattrapage de l'existant : chiffre les fichiers déjà sur le disque.
 *
 * <h2>Pourquoi il faut une tâche, et pas seulement un drapeau</h2>
 *
 * <p>Allumer le chiffrement ne protège que ce qui s'écrit ensuite. Les images
 * accumulées depuis l'ouverture du laboratoire restent en clair, et ce sont
 * elles que représente le volume d'un vol de disque. Sans ce rattrapage, le
 * chiffrement donne surtout l'impression d'être protégé.</p>
 *
 * <h2>Ce qui rend l'opération sûre</h2>
 *
 * <p>Chaque fichier est <b>relu déchiffré et comparé à l'original avant</b> que
 * quoi que ce soit ne soit remplacé. Un défaut dans le chiffreur détruirait
 * sinon les images une par une, en silence, et on ne s'en apercevrait qu'à la
 * première consultation d'un ancien dossier.</p>
 *
 * <p>Le remplacement passe par un fichier temporaire forcé sur le disque, puis
 * un renommage atomique. Une coupure de courant laisse alors soit l'ancien
 * fichier intact, soit le nouveau complet — jamais un fichier à moitié
 * réécrit.</p>
 *
 * <p>L'opération est <b>reprenable</b> : un fichier déjà chiffré est reconnu et
 * sauté. La relancer après une interruption reprend où elle en était, sans
 * jamais chiffrer deux fois.</p>
 *
 * <h2>Elle ne part jamais toute seule</h2>
 *
 * <p>Aucun {@code @Scheduled}, aucun déclenchement au démarrage. Elle se demande
 * explicitement, et son mode par défaut est la <b>simulation</b> : un appel sans
 * paramètre ne touche à rien et se contente de compter.</p>
 */
@Slf4j
@Service
public class StorageEncryptionBackfill {

    /** Suffixe des fichiers de travail, sautés par le parcours. */
    private static final String SUFFIXE_TRAVAIL = ".chiffrement-en-cours";

    private final Path racine;
    private final ObjectProvider<FileCipher> chiffreur;
    private final DepotDisque disque;

    /** Deux rattrapages simultanés se disputeraient les mêmes fichiers. */
    private final AtomicBoolean enCours = new AtomicBoolean(false);

    public StorageEncryptionBackfill(@Value("${app.storage.path:/tmp/labo/storage}") String racine,
                                     ObjectProvider<FileCipher> chiffreur,
                                     DepotDisque disque) {
        this.racine = Paths.get(racine).toAbsolutePath().normalize();
        this.chiffreur = chiffreur;
        this.disque = disque;
    }

    /**
     * Bilan d'un passage.
     *
     * @param examines     fichiers rencontrés
     * @param dejaChiffres sautés parce que déjà au format
     * @param chiffres     effectivement chiffrés — ou qui l'auraient été, en simulation
     * @param echecs       laissés intacts faute d'avoir pu être chiffrés sûrement
     * @param octets       volume en clair traité
     * @param details      les premiers échecs, pour savoir où regarder
     */
    public record Bilan(boolean simulation, int examines, int dejaChiffres, int chiffres,
                        int echecs, long octets, List<String> details) {}

    /**
     * @param simulation vrai : ne touche à rien, compte seulement. C'est le défaut.
     * @param limite     nombre maximum de fichiers à chiffrer, {@code <= 0} pour tous.
     *                   Sert à procéder par lots sur un gros stockage.
     */
    public Bilan rattraper(boolean simulation, int limite) {
        if (chiffreur.getIfAvailable() == null) {
            throw new InvalidOperationException(
                    "Le chiffrement est désactivé : rien à rattraper. "
                    + "Poser app.storage.encryption.enabled à true d'abord.");
        }
        if (!Files.isDirectory(racine)) {
            throw new InvalidOperationException("Racine de stockage introuvable : " + racine);
        }
        if (!enCours.compareAndSet(false, true)) {
            throw new InvalidOperationException("Un rattrapage est déjà en cours");
        }
        try {
            return parcourir(simulation, limite);
        } finally {
            enCours.set(false);
        }
    }

    private Bilan parcourir(boolean simulation, int limite) {
        FileCipher c = chiffreur.getObject();
        // Disque seul : ce rattrapage parcourt le dossier de stockage et
        // réécrit sur place. Lui donner le dépôt distant enverrait la version
        // chiffrée dans le seau en laissant le clair ici.
        StoredFiles sonde = StoredFiles.surLeDisqueSeul(chiffreur, disque);
        int examines = 0, deja = 0, faits = 0, echecs = 0;
        long octets = 0;
        List<String> details = new ArrayList<>();

        log.info("Rattrapage du chiffrement {} sur {}",
                simulation ? "(SIMULATION)" : "(RÉEL)", racine);

        try (Stream<Path> parcours = Files.walk(racine)) {
            for (Path f : (Iterable<Path>) parcours.filter(Files::isRegularFile)::iterator) {
                if (f.getFileName().toString().endsWith(SUFFIXE_TRAVAIL)) {
                    continue;   // reliquat d'un passage interrompu
                }
                examines++;
                try {
                    if (sonde.estChiffre(f)) {
                        deja++;
                        continue;
                    }
                    if (limite > 0 && faits >= limite) {
                        continue;   // on continue de compter, sans plus rien écrire
                    }
                    long taille = Files.size(f);
                    if (!simulation) {
                        chiffrerSurPlace(c, f);
                    }
                    faits++;
                    octets += taille;
                    if (faits % 200 == 0) {
                        log.info("Rattrapage : {} fichiers traités", faits);
                    }
                } catch (Exception e) {
                    echecs++;
                    // Le fichier reste intact : l'échec est survenu avant tout
                    // remplacement, ou le temporaire a été retiré.
                    log.warn("Rattrapage : {} laissé intact — {}", f, e.getMessage());
                    if (details.size() < 20) {
                        details.add(racine.relativize(f) + " : " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            throw new InvalidOperationException("Parcours du stockage impossible : " + e.getMessage());
        }

        log.info("Rattrapage terminé : {} examinés, {} déjà chiffrés, {} chiffrés, {} échecs",
                examines, deja, faits, echecs);
        return new Bilan(simulation, examines, deja, faits, echecs, octets, details);
    }

    /**
     * Chiffre un fichier en place, sans jamais le laisser dans un état partiel.
     */
    private void chiffrerSurPlace(FileCipher c, Path fichier) throws IOException {
        byte[] clair = Files.readAllBytes(fichier);
        byte[] chiffre = c.chiffrer(clair);

        // La vérification qui rend l'opération acceptable. Sans elle, un défaut
        // du chiffreur détruirait les images une par une, en silence.
        if (!Arrays.equals(c.dechiffrer(chiffre), clair)) {
            throw new IOException("Relecture différente de l'original — fichier laissé intact");
        }

        Path travail = fichier.resolveSibling(fichier.getFileName() + SUFFIXE_TRAVAIL);
        try {
            try (FileChannel canal = FileChannel.open(travail,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                canal.write(ByteBuffer.wrap(chiffre));
                // Forcer sur le disque AVANT le renommage : sans cela, une coupure
                // de courant pourrait laisser un fichier renommé mais vide, et
                // l'original serait perdu.
                canal.force(true);
            }
            // Dans le même répertoire, donc le même système de fichiers : le
            // renommage est atomique, et le fichier n'est jamais absent.
            Files.move(travail, fichier, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.deleteIfExists(travail);
            throw e;
        }
    }
}

package com.labo.anapath.common.storage;

import com.labo.anapath.common.exception.InvalidOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Le seul endroit du projet où un fichier stocké s'écrit et se relit.
 *
 * <h2>Pourquoi un point de passage</h2>
 *
 * Ce projet a <b>trois</b> services de stockage — {@code common.storage},
 * {@code hr} et {@code testorder} — qui écrivent tous dans la même racine, et
 * <b>deux</b> chemins de lecture : {@link FileController} pour {@code /api/v1/files/**}
 * et {@code hr.FileStorageServiceImpl.load} pour les documents du personnel.
 * Cinq endroits, donc cinq occasions d'en oublier un. Un fichier écrit chiffré
 * mais relu par un chemin resté naïf serait servi tel quel : le navigateur
 * afficherait une image cassée, et le défaut ne se verrait qu'en production.
 *
 * <p>Tout passe donc ici, et la décision de chiffrer se prend une fois.</p>
 *
 * <h2>Chiffrement éteint : rien ne change</h2>
 *
 * <p>Sans {@code app.storage.encryption.enabled}, {@link FileCipher} n'existe
 * pas et ce composant se contente de recopier les octets — le
 * {@code Files.copy} d'avant, au même endroit. La lecture ne charge alors rien
 * en mémoire : elle rend la main au flux, comme aujourd'hui.</p>
 *
 * <h2>Lecture tolérante</h2>
 *
 * <p>La décision de déchiffrer ne vient pas de la configuration mais du
 * <b>fichier lui-même</b>, reconnu à sa marque de tête. Les deux sortes
 * cohabitent donc sans inventaire : les images déjà sur le disque restent
 * lisibles le jour où le chiffrement s'allume, et le rattrapage de l'existant
 * peut se faire plus tard, à froid.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StoredFiles {

    /**
     * Le chiffreur n'existe que si le chiffrement est activé. Un
     * {@link ObjectProvider} plutôt qu'une injection directe : ce composant-ci
     * doit exister dans les deux cas, puisqu'il porte aussi le chemin en clair.
     */
    private final ObjectProvider<FileCipher> chiffreur;

    /** Le chiffrement est-il actif pour les écritures à venir ? */
    public boolean chiffrementActif() {
        return chiffreur.getIfAvailable() != null;
    }

    /**
     * Écrit un fichier téléversé à l'emplacement voulu.
     *
     * <p>Chiffré si le chiffrement est actif, recopié tel quel sinon. Le nom du
     * fichier ne change pas : l'extension est conservée, donc le type de contenu
     * reste déductible et aucun chemin n'a à être migré en base.</p>
     */
    public void ecrire(MultipartFile source, Path cible) throws IOException {
        FileCipher c = chiffreur.getIfAvailable();
        if (c == null) {
            try (InputStream in = source.getInputStream()) {
                Files.copy(in, cible, StandardCopyOption.REPLACE_EXISTING);
            }
            return;
        }
        // GCM scelle l'ensemble : il n'y a pas de chiffrement en flux qui
        // permette d'authentifier au fil de l'eau sans garder le tout. Le
        // téléversement est déjà plafonné à 50 Mo par Spring, et le fichier
        // vient de traverser la mémoire pour arriver ici.
        byte[] clair = source.getBytes();
        Files.write(cible, c.chiffrer(clair));
    }

    /** Écrit un contenu déjà en mémoire — sert au rattrapage de l'existant. */
    public void ecrire(byte[] clair, Path cible) throws IOException {
        FileCipher c = chiffreur.getIfAvailable();
        Files.write(cible, c == null ? clair : c.chiffrer(clair));
    }

    /**
     * Ce fichier porte-t-il la marque du format chiffré ?
     *
     * <p>Ne lit que l'en-tête. C'est ce qui permet aux lecteurs de garder le
     * flux pour les fichiers en clair, au lieu de charger 50 Mo en mémoire pour
     * découvrir qu'il n'y avait rien à déchiffrer.</p>
     */
    public boolean estChiffre(Path fichier) throws IOException {
        // Un fichier absent n'est pas chiffré — il est absent. Lever ici
        // remplacerait le « Fichier physique introuvable » que les appelants
        // savent rendre par une erreur de lecture, sur un cas qui arrive :
        // une ligne en base dont le fichier a disparu du disque.
        if (!Files.isRegularFile(fichier) || Files.size(fichier) < FileCipher.TAILLE_ENTETE) {
            return false;
        }
        byte[] tete = new byte[FileCipher.TAILLE_ENTETE_RECONNAISSANCE];
        try (InputStream in = Files.newInputStream(fichier)) {
            if (in.readNBytes(tete, 0, tete.length) != tete.length) {
                return false;
            }
        }
        return FileCipher.porteLaMarque(tete);
    }

    /**
     * Rend le contenu en clair d'un fichier chiffré.
     *
     * <p>À n'appeler qu'après {@link #estChiffre}. Un fichier chiffré alors que
     * le chiffrement a été éteint depuis est refusé explicitement : servir ses
     * octets tels quels donnerait une image cassée, et l'exploitant chercherait
     * la panne du côté du navigateur.</p>
     */
    public byte[] lireDechiffre(Path fichier) throws IOException {
        FileCipher c = chiffreur.getIfAvailable();
        if (c == null) {
            log.error("Fichier chiffré {} mais le chiffrement est désactivé : "
                      + "remettre app.storage.encryption.enabled à true", fichier);
            throw new InvalidOperationException(
                    "Ce fichier est chiffré mais le chiffrement est désactivé sur le serveur");
        }
        return c.dechiffrer(Files.readAllBytes(fichier));
    }
}

package com.labo.anapath.common.storage;

import com.labo.anapath.common.exception.InvalidOperationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

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

    /** Toujours là : il détient les fichiers d'avant la bascule. */
    private final DepotDisque disque;

    /**
     * Présent seulement si S3 est configuré.
     *
     * <p>Quand il l'est : on écrit chez lui, et on relit chez lui d'abord. Ce
     * qu'il ne connaît pas est cherché sur le disque — c'est ainsi que les
     * clichés d'hier restent lisibles sans migration en bloc, et que la bascule
     * se défait en retirant une ligne de configuration.</p>
     */
    private final ObjectProvider<DepotS3> distant;

    /**
     * Une instance qui ne connaît que le disque.
     *
     * <p>Pour le rattrapage du chiffrement, qui parcourt le dossier de stockage
     * fichier par fichier. Lui donner le dépôt distant lui ferait écrire la
     * version chiffrée dans le seau <em>en laissant le clair sur le disque</em> —
     * l'inverse exact de ce qu'il vient faire.</p>
     */
    static StoredFiles surLeDisqueSeul(ObjectProvider<FileCipher> chiffreur,
                                       DepotDisque disque) {
        return new StoredFiles(chiffreur, disque, null);
    }

    /** Où écrire : le distant s'il existe, le disque sinon. */
    private DepotDOctets pourEcrire() {
        DepotDOctets s3 = distant == null ? null : distant.getIfAvailable();
        return s3 != null ? s3 : disque;
    }

    /**
     * Les dépôts à interroger pour une lecture, dans l'ordre.
     *
     * <p>Le distant d'abord : c'est là que sont les fichiers récents, donc la
     * plupart des lectures. Le disque ensuite, pour l'existant.</p>
     */
    private java.util.List<DepotDOctets> pourLire() {
        DepotDOctets s3 = distant == null ? null : distant.getIfAvailable();
        return s3 == null ? java.util.List.of(disque) : java.util.List.of(s3, disque);
    }

    /**
     * La clé d'un chemin, relative à la racine du stockage.
     *
     * <p>Les trois services appelants raisonnent en chemins absolus ; le dépôt
     * raisonne en clés. La conversion est ici, et nulle part ailleurs.</p>
     */
    private String cle(Path fichier) {
        Path abs = fichier.toAbsolutePath().normalize();
        Path racine = disque.racine();
        String relative = abs.startsWith(racine)
                ? racine.relativize(abs).toString()
                : abs.getFileName().toString();
        // S3 ne connaît que la barre oblique, y compris quand le serveur tourne
        // sous un système qui sépare autrement.
        return relative.replace(java.io.File.separatorChar, '/');
    }

    /** Le nom du dépôt d'écriture, pour les journaux et l'écran d'administration. */
    public String depotCourant() {
        return pourEcrire().nom();
    }

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
        // GCM scelle l'ensemble : il n'y a pas de chiffrement en flux qui
        // permette d'authentifier au fil de l'eau sans garder le tout. Le
        // téléversement est déjà plafonné à 50 Mo par Spring, et le fichier
        // vient de traverser la mémoire pour arriver ici. Le dépôt distant
        // demande de toute façon le contenu entier.
        byte[] contenu = source.getBytes();
        pourEcrire().ecrire(cle(cible), c == null ? contenu : c.chiffrer(contenu));
    }

    /** Écrit un contenu déjà en mémoire — sert au rattrapage de l'existant. */
    public void ecrire(byte[] clair, Path cible) throws IOException {
        FileCipher c = chiffreur.getIfAvailable();
        pourEcrire().ecrire(cle(cible), c == null ? clair : c.chiffrer(clair));
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
        // une ligne en base dont le fichier a disparu du dépôt.
        String k = cle(fichier);
        for (DepotDOctets depot : pourLire()) {
            byte[] tete = depot.lireLeDebut(k, FileCipher.TAILLE_ENTETE_RECONNAISSANCE);
            if (tete == null) continue;
            if (tete.length < FileCipher.TAILLE_ENTETE_RECONNAISSANCE) return false;
            return FileCipher.porteLaMarque(tete);
        }
        return false;
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
        return c.dechiffrer(lireBrut(fichier));
    }

    /**
     * Le contenu tel qu'il est stocké, sans le déchiffrer.
     *
     * <p>Cherché dans le dépôt distant puis sur le disque. Un fichier introuvable
     * des deux côtés lève : rendre un tableau vide ferait servir une image de
     * zéro octet, et l'écran afficherait un cadre cassé au lieu d'une erreur.</p>
     */
    public byte[] lireBrut(Path fichier) throws IOException {
        String k = cle(fichier);
        for (DepotDOctets depot : pourLire()) {
            byte[] octets = depot.lire(k);
            if (octets != null) return octets;
        }
        throw new java.io.FileNotFoundException(k);
    }

    /** Existe-t-il, ici ou là ? */
    public boolean existe(Path fichier) throws IOException {
        String k = cle(fichier);
        for (DepotDOctets depot : pourLire()) {
            if (depot.existe(k)) return true;
        }
        return false;
    }

    /** La taille stockée — chiffrée quand le contenu l'est. */
    public long taille(Path fichier) throws IOException {
        String k = cle(fichier);
        for (DepotDOctets depot : pourLire()) {
            long t = depot.taille(k);
            if (t >= 0) return t;
        }
        return -1;
    }

    /**
     * Supprime partout.
     *
     * <p>Des deux côtés, et non du seul dépôt d'écriture : un fichier d'avant la
     * bascule vit encore sur le disque, et ne supprimer que dans le seau le
     * laisserait revenir à la lecture suivante — effacé aux yeux de l'agent,
     * toujours présent en fait.</p>
     */
    public void supprimer(Path fichier) throws IOException {
        String k = cle(fichier);
        IOException premiere = null;
        for (DepotDOctets depot : pourLire()) {
            try {
                depot.supprimer(k);
            } catch (IOException e) {
                log.warn("Suppression impossible sur {} : {}", depot.nom(), e.getMessage());
                if (premiere == null) premiere = e;
            }
        }
        if (premiere != null) throw premiere;
    }
}

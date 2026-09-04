package com.labo.anapath.common.storage;

import java.io.IOException;

/**
 * Où vivent réellement les octets d'un fichier.
 *
 * <h2>Pourquoi cette couche</h2>
 *
 * <p>Trois services calculent des chemins de fichiers, chacun dans son coin, et
 * confient les octets à {@link StoredFiles}. Ils continuent de raisonner en
 * chemins ; c'est ici seulement qu'on décide si ce chemin désigne un fichier du
 * disque ou un objet dans un seau. Sans cette séparation, passer à S3 aurait
 * voulu dire réécrire les trois.</p>
 *
 * <h2>Ce qui n'est pas de son ressort</h2>
 *
 * <p>Le chiffrement. Les octets qu'on lui remet sont déjà chiffrés, et ceux
 * qu'il rend le sont encore : le dépôt déplace des données qu'il ne peut pas
 * lire. C'est ce qui permet de confier le stockage à un tiers sans lui confier
 * le contenu.</p>
 */
public interface DepotDOctets {

    /** Un nom lisible dans les journaux — « disque », « S3 ». */
    String nom();

    /**
     * Écrit, en remplaçant ce qui s'y trouvait.
     *
     * @param cle    chemin relatif à la racine du stockage, séparé par des « / »
     * @param octets le contenu, déjà chiffré s'il doit l'être
     */
    void ecrire(String cle, byte[] octets) throws IOException;

    /** Rend le contenu, ou {@code null} si la clé n'existe pas ici. */
    byte[] lire(String cle) throws IOException;

    /**
     * Rend les premiers octets, sans rapatrier le reste.
     *
     * <p>Sert à reconnaître un fichier chiffré à sa marque. Lire un cliché de
     * lame entier pour en examiner seize octets coûterait, sur un stockage
     * distant, autant que de le servir.</p>
     *
     * @return au plus {@code combien} octets, ou {@code null} si la clé
     *         n'existe pas ici
     */
    byte[] lireLeDebut(String cle, int combien) throws IOException;

    boolean existe(String cle) throws IOException;

    /** La taille stockée — chiffrée, donc, quand le contenu l'est. */
    long taille(String cle) throws IOException;

    /** Supprime. Ne se plaint pas d'une clé absente. */
    void supprimer(String cle) throws IOException;
}

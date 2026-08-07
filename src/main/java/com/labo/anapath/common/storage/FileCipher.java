package com.labo.anapath.common.storage;

import com.labo.anapath.common.exception.InvalidOperationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Chiffrement des fichiers stockés, par encapsulation de clé.
 *
 * <h2>Le principe</h2>
 *
 * Chaque fichier reçoit sa propre clé, tirée au hasard, qui chiffre son
 * contenu. Cette clé est elle-même chiffrée par une clé maîtresse et voyage
 * dans l'en-tête du fichier.
 *
 * <p>L'intérêt est la <b>rotation</b> : changer la clé maîtresse ne demande que
 * de rechiffrer les clés encapsulées — quelques dizaines d'octets par fichier —
 * jamais les images elles-mêmes.</p>
 *
 * <h2>Ce que cela protège, et ce que non</h2>
 *
 * <p>La clé maîtresse vit dans une variable d'environnement du serveur. Un
 * volume Docker emporté, un disque de serveur éteint ou une sauvegarde de
 * fichiers dérobée ne livrent donc rien. Un serveur <b>en fonctionnement</b>,
 * lui, livre tout : l'attaquant y lit la variable comme l'application le
 * fait.</p>
 *
 * <h2>Le format</h2>
 *
 * <pre>
 *   0    4   « LABO »        marque de reconnaissance
 *   4    1   version         pour faire évoluer le format sans casser l'existant
 *   5   12   IV              de l'encapsulation de la clé
 *  17   48   clé encapsulée  32 octets de clé + 16 de sceau
 *  65   12   IV              du contenu
 *  77    8   taille en clair
 *  85   ..   contenu chiffré + sceau
 * </pre>
 *
 * <p>La <b>taille en clair</b> n'est pas un ornement. Le service qui rend les
 * fichiers pose {@code Content-Length} depuis la taille sur disque ; après
 * chiffrement, elle ne correspond plus. Et le {@code Transfer-Encoding: chunked}
 * a déjà été écarté de ce projet, le navigateur le rejetant.</p>
 *
 * <p>Le nom du fichier ne change pas : {@code <uuid>.jpeg} reste
 * {@code <uuid>.jpeg}. Le type de contenu se déduit de l'extension, et aucun
 * chemin n'a à être migré en base.</p>
 *
 * <h2>Pourquoi GCM</h2>
 *
 * <p>Il authentifie en même temps qu'il chiffre : un fichier altéré sur le
 * disque est <b>refusé</b>, jamais déchiffré de travers. Sans cela, une
 * corruption silencieuse produirait des octets plausibles.</p>
 *
 * <h2>Ce composant ne s'active pas tout seul</h2>
 *
 * <p>Il n'existe que si {@code app.storage.encryption.enabled} vaut vrai. Tant
 * que ce n'est pas le cas, l'ajouter au projet ne change rien — ni au
 * démarrage, ni aux fichiers. Une fois activé, l'absence de clé empêche le
 * démarrage : une application qui accepterait de démarrer sans clé servirait
 * une erreur à chaque image, ce qui se découvre trop tard.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.storage.encryption.enabled", havingValue = "true")
public class FileCipher {

    /** Marque de tête : reconnaît un fichier chiffré sans avoir à le déchiffrer. */
    static final byte[] MAGIE = {'L', 'A', 'B', 'O'};

    static final byte VERSION = 1;

    /**
     * Plafond des versions plausibles, pour distinguer la marque d'un hasard.
     *
     * <p>« LABO » n'est pas une suite d'octets improbable : c'est le début de
     * « LABORATOIRE ». Un document texte ou un CSV commençant par ce mot serait
     * pris pour un fichier chiffré — et le service RH accepte n'importe quelle
     * extension, donc n'importe quel contenu. L'octet suivant lève l'ambiguïté :
     * dans « LABORATOIRE » c'est « R » (82), hors de portée d'un numéro de
     * version.</p>
     *
     * <p>Le plafond, plutôt que l'égalité stricte à {@link #VERSION} : un fichier
     * d'un format futur, relu par cette version du code après un retour arrière,
     * doit être <b>refusé bruyamment</b> et non servi comme du clair. Un refus se
     * remarque ; des octets chiffrés servis en image ne se voient qu'à l'écran.</p>
     */
    static final byte VERSION_MAX = 16;

    private static final int TAILLE_IV = 12;          // recommandation GCM
    private static final int TAILLE_SCEAU_BITS = 128;
    private static final int TAILLE_CLE = 32;         // AES-256
    private static final int TAILLE_CLE_ENCAPSULEE = TAILLE_CLE + TAILLE_SCEAU_BITS / 8;

    static final int OFFSET_VERSION = MAGIE.length;
    static final int OFFSET_IV_CLE = OFFSET_VERSION + 1;
    static final int OFFSET_CLE = OFFSET_IV_CLE + TAILLE_IV;
    static final int OFFSET_IV_CONTENU = OFFSET_CLE + TAILLE_CLE_ENCAPSULEE;
    static final int OFFSET_TAILLE = OFFSET_IV_CONTENU + TAILLE_IV;
    static final int TAILLE_ENTETE = OFFSET_TAILLE + Long.BYTES;

    /** Nombre d'octets de tête nécessaires pour se prononcer sur la sorte du fichier. */
    static final int TAILLE_ENTETE_RECONNAISSANCE = OFFSET_VERSION + 1;

    private final SecretKey cleMaitresse;
    private final SecureRandom alea = new SecureRandom();

    /**
     * @param cleBase64 clé maîtresse, 32 octets encodés en base64. Se produit par
     *                  {@code openssl rand -base64 32}.
     */
    public FileCipher(@Value("${app.storage.encryption.key:}") String cleBase64) {
        if (cleBase64 == null || cleBase64.isBlank()) {
            throw new IllegalStateException(
                    "Chiffrement activé mais app.storage.encryption.key est vide. "
                    + "Produire une clé par « openssl rand -base64 32 » et la placer "
                    + "dans l'environnement du serveur — AVEC une copie hors serveur : "
                    + "une clé perdue rend les fichiers définitivement illisibles.");
        }
        byte[] brut;
        try {
            brut = Base64.getDecoder().decode(cleBase64.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "app.storage.encryption.key n'est pas du base64 valide", e);
        }
        if (brut.length != TAILLE_CLE) {
            throw new IllegalStateException(
                    "La clé maîtresse doit faire " + TAILLE_CLE + " octets une fois décodée, "
                    + "or elle en fait " + brut.length + ".");
        }
        this.cleMaitresse = new SecretKeySpec(brut, "AES");
        Arrays.fill(brut, (byte) 0);
        log.info("Chiffrement des fichiers actif (AES-256-GCM, encapsulation de clé)");
    }

    /**
     * Un contenu porte-t-il la marque du format ?
     *
     * <p>Sert à la lecture tolérante : tant que la reprise de l'existant n'est
     * pas faite, les deux sortes de fichiers cohabitent.</p>
     */
    public static boolean estChiffre(byte[] contenu) {
        if (contenu == null || contenu.length < TAILLE_ENTETE) {
            return false;
        }
        return porteLaMarque(Arrays.copyOf(contenu, TAILLE_ENTETE_RECONNAISSANCE));
    }

    /**
     * La marque ET une version plausible. Les deux, jamais l'une sans l'autre —
     * voir {@link #VERSION_MAX}.
     */
    static boolean porteLaMarque(byte[] tete) {
        if (tete.length < TAILLE_ENTETE_RECONNAISSANCE
                || !Arrays.equals(Arrays.copyOf(tete, MAGIE.length), MAGIE)) {
            return false;
        }
        byte version = tete[OFFSET_VERSION];
        return version > 0 && version <= VERSION_MAX;
    }

    /** Taille du clair, lue dans l'en-tête sans déchiffrer le contenu. */
    public static long tailleEnClair(byte[] chiffre) {
        if (!estChiffre(chiffre)) {
            throw new InvalidOperationException("Ce contenu n'est pas chiffré");
        }
        return ByteBuffer.wrap(chiffre, OFFSET_TAILLE, Long.BYTES).getLong();
    }

    /** Chiffre un contenu et rend le fichier complet, en-tête compris. */
    public byte[] chiffrer(byte[] clair) {
        if (clair == null) {
            throw new InvalidOperationException("Contenu absent");
        }
        try {
            byte[] cleFichier = new byte[TAILLE_CLE];
            alea.nextBytes(cleFichier);
            byte[] ivContenu = new byte[TAILLE_IV];
            alea.nextBytes(ivContenu);
            byte[] ivCle = new byte[TAILLE_IV];
            alea.nextBytes(ivCle);

            Cipher contenu = Cipher.getInstance("AES/GCM/NoPadding");
            contenu.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cleFichier, "AES"),
                    new GCMParameterSpec(TAILLE_SCEAU_BITS, ivContenu));
            byte[] chiffre = contenu.doFinal(clair);

            Cipher encapsulage = Cipher.getInstance("AES/GCM/NoPadding");
            encapsulage.init(Cipher.ENCRYPT_MODE, cleMaitresse,
                    new GCMParameterSpec(TAILLE_SCEAU_BITS, ivCle));
            // La version entre dans les données authentifiées : un fichier dont
            // on maquillerait la version pour viser un autre format de lecture
            // ferait échouer l'ouverture de la clé, pas seulement celle du contenu.
            encapsulage.updateAAD(new byte[]{VERSION});
            byte[] cleEncapsulee = encapsulage.doFinal(cleFichier);
            Arrays.fill(cleFichier, (byte) 0);

            ByteBuffer sortie = ByteBuffer.allocate(TAILLE_ENTETE + chiffre.length);
            sortie.put(MAGIE).put(VERSION).put(ivCle).put(cleEncapsulee)
                    .put(ivContenu).putLong(clair.length).put(chiffre);
            return sortie.array();
        } catch (GeneralSecurityException e) {
            throw new InvalidOperationException("Chiffrement impossible : " + e.getMessage());
        }
    }

    /**
     * Déchiffre un fichier produit par {@link #chiffrer}.
     *
     * @throws InvalidOperationException si l'en-tête est absent ou tronqué, si la
     *         version est inconnue, si le contenu a été altéré, ou si la clé
     *         maîtresse n'est pas celle du chiffrement. Dans tous les cas rien
     *         n'est rendu : jamais de contenu partiel.
     */
    public byte[] dechiffrer(byte[] fichier) {
        if (fichier == null || fichier.length < TAILLE_ENTETE) {
            throw new InvalidOperationException(
                    "Fichier chiffré tronqué : en-tête incomplet");
        }
        if (!porteLaMarque(Arrays.copyOf(fichier, TAILLE_ENTETE_RECONNAISSANCE))) {
            throw new InvalidOperationException("Ce fichier n'est pas chiffré");
        }
        byte version = fichier[OFFSET_VERSION];
        if (version != VERSION) {
            throw new InvalidOperationException(
                    "Version de chiffrement inconnue : " + version);
        }
        try {
            byte[] ivCle = Arrays.copyOfRange(fichier, OFFSET_IV_CLE, OFFSET_CLE);
            byte[] cleEncapsulee = Arrays.copyOfRange(fichier, OFFSET_CLE, OFFSET_IV_CONTENU);
            byte[] ivContenu = Arrays.copyOfRange(fichier, OFFSET_IV_CONTENU, OFFSET_TAILLE);

            Cipher ouverture = Cipher.getInstance("AES/GCM/NoPadding");
            ouverture.init(Cipher.DECRYPT_MODE, cleMaitresse,
                    new GCMParameterSpec(TAILLE_SCEAU_BITS, ivCle));
            ouverture.updateAAD(new byte[]{version});
            byte[] cleFichier = ouverture.doFinal(cleEncapsulee);

            Cipher contenu = Cipher.getInstance("AES/GCM/NoPadding");
            contenu.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cleFichier, "AES"),
                    new GCMParameterSpec(TAILLE_SCEAU_BITS, ivContenu));
            byte[] clair = contenu.doFinal(
                    fichier, TAILLE_ENTETE, fichier.length - TAILLE_ENTETE);
            Arrays.fill(cleFichier, (byte) 0);

            long annoncee = ByteBuffer.wrap(fichier, OFFSET_TAILLE, Long.BYTES).getLong();
            if (annoncee != clair.length) {
                // Le sceau GCM garantit déjà l'intégrité ; ce contrôle vise
                // l'en-tête, qui n'est pas couvert par lui. Une taille fausse
                // produirait un Content-Length faux, donc une réponse tronquée.
                throw new InvalidOperationException(
                        "Taille annoncée (" + annoncee + ") différente du contenu déchiffré ("
                        + clair.length + ")");
            }
            return clair;
        } catch (GeneralSecurityException e) {
            // Message volontairement identique pour une clé fausse et un contenu
            // altéré : distinguer les deux renseignerait un attaquant sur ce qui
            // a échoué.
            throw new InvalidOperationException(
                    "Déchiffrement refusé : contenu altéré ou clé incorrecte");
        }
    }
}

package com.labo.anapath.common.storage;

import com.labo.anapath.common.exception.InvalidOperationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Jeux d'essai du chiffrement des fichiers.
 *
 * <p>Quatre cas valident le fonctionnement, les autres valident les
 * <b>refus</b>. C'est là qu'un chiffrement se juge : un déchiffrement qui rend
 * des octets plausibles au lieu de refuser est pire que pas de chiffrement du
 * tout, puisqu'on lui fait confiance.</p>
 */
class FileCipherTest {

    private static String cleAleatoire() {
        byte[] k = new byte[32];
        new SecureRandom().nextBytes(k);
        return Base64.getEncoder().encodeToString(k);
    }

    private static FileCipher chiffreur() {
        return new FileCipher(cleAleatoire());
    }

    private static byte[] image(int taille) {
        byte[] o = new byte[taille];
        new SecureRandom().nextBytes(o);
        return o;
    }

    // ── Fonctionnement ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Une image revient identique, octet pour octet")
    void allerRetourIdentique() {
        FileCipher c = chiffreur();
        byte[] clair = image(49_152);            // 48 Ko, la moyenne du parc

        byte[] chiffre = c.chiffrer(clair);

        assertThat(chiffre).isNotEqualTo(clair);
        assertThat(c.dechiffrer(chiffre)).isEqualTo(clair);
    }

    @Test
    @DisplayName("Un fichier vide passe sans erreur")
    void fichierVide() {
        // GCM sur une entrée vide produit un cryptogramme NON vide — le sceau
        // seul. Le cas casse les implémentations qui supposent l'inverse.
        FileCipher c = chiffreur();

        byte[] chiffre = c.chiffrer(new byte[0]);

        assertThat(chiffre.length).isGreaterThan(0);
        assertThat(c.dechiffrer(chiffre)).isEmpty();
        assertThat(FileCipher.tailleEnClair(chiffre)).isZero();
    }

    @Test
    @DisplayName("Un gros fichier passe aussi")
    void grosFichier() {
        FileCipher c = chiffreur();
        byte[] clair = image(5 * 1024 * 1024);

        assertThat(c.dechiffrer(c.chiffrer(clair))).isEqualTo(clair);
    }

    @Test
    @DisplayName("Deux chiffrements du même contenu donnent deux cryptogrammes différents")
    void memeContenuDeuxCryptogrammes() {
        // Sinon un observateur du disque saurait que deux fichiers sont
        // identiques — pour des images médicales, c'est déjà une fuite.
        FileCipher c = chiffreur();
        byte[] clair = "coupe histologique".getBytes(StandardCharsets.UTF_8);

        assertThat(c.chiffrer(clair)).isNotEqualTo(c.chiffrer(clair));
    }

    @Test
    @DisplayName("La taille en clair se lit dans l'en-tête, sans déchiffrer")
    void tailleLisibleSansDechiffrer() {
        // Le service qui rend les fichiers pose Content-Length AVANT de lire le
        // contenu ; la taille sur disque, elle, est celle du chiffré.
        FileCipher c = chiffreur();
        byte[] clair = image(12_345);

        byte[] chiffre = c.chiffrer(clair);

        assertThat(FileCipher.tailleEnClair(chiffre)).isEqualTo(12_345);
        assertThat(chiffre.length).isNotEqualTo(12_345);
    }

    // ── Reconnaissance ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Un fichier en clair est reconnu comme tel")
    void fichierEnClairReconnu() {
        // Sur quoi repose la lecture tolérante : tant que la reprise n'est pas
        // faite, les deux sortes de fichiers cohabitent.
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

        assertThat(FileCipher.estChiffre(jpeg)).isFalse();
        assertThat(FileCipher.estChiffre(chiffreur().chiffrer(jpeg))).isTrue();
    }

    @Test
    @DisplayName("Un contenu plus court que l'en-tête n'est pas pris pour du chiffré")
    void contenuPlusCourtQueLEntete() {
        assertThat(FileCipher.estChiffre(new byte[]{'L', 'A', 'B', 'O'})).isFalse();
        assertThat(FileCipher.estChiffre(new byte[0])).isFalse();
        assertThat(FileCipher.estChiffre(null)).isFalse();
    }

    // ── Refus ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Un contenu altéré est refusé, jamais déchiffré de travers")
    void contenuAltere() {
        FileCipher c = chiffreur();
        byte[] chiffre = c.chiffrer(image(4096));
        chiffre[chiffre.length - 20] ^= 0x01;     // un seul bit retourné

        assertThatThrownBy(() -> c.dechiffrer(chiffre))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("altéré ou clé incorrecte");
    }

    @Test
    @DisplayName("Une clé encapsulée altérée est refusée")
    void cleEncapsuleeAlteree() {
        FileCipher c = chiffreur();
        byte[] chiffre = c.chiffrer(image(4096));
        chiffre[FileCipher.OFFSET_CLE + 3] ^= 0x01;

        assertThatThrownBy(() -> c.dechiffrer(chiffre))
                .isInstanceOf(InvalidOperationException.class);
    }

    @Test
    @DisplayName("Un fichier tronqué est refusé, avec un message distinct")
    void fichierTronque() {
        // Un disque plein ou une écriture interrompue produisent exactement cela.
        FileCipher c = chiffreur();
        byte[] chiffre = c.chiffrer(image(4096));
        byte[] tronque = java.util.Arrays.copyOf(chiffre, 12);

        assertThatThrownBy(() -> c.dechiffrer(tronque))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("tronqué");
    }

    @Test
    @DisplayName("Une mauvaise clé maîtresse est refusée, sans rien rendre")
    void mauvaiseCle() {
        byte[] clair = image(4096);
        byte[] chiffre = chiffreur().chiffrer(clair);
        FileCipher autre = chiffreur();          // autre clé maîtresse

        assertThatThrownBy(() -> autre.dechiffrer(chiffre))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("altéré ou clé incorrecte");
    }

    @Test
    @DisplayName("Une version plausible mais inconnue est refusée comme telle")
    void versionInconnue() {
        // Le cas du retour arrière : du v2 écrit, relu par du code qui ne connaît
        // que v1. Reconnu comme chiffré, donc refusé bruyamment — et non servi en
        // clair, ce qui donnerait des octets chiffrés affichés en image.
        FileCipher c = chiffreur();
        byte[] chiffre = c.chiffrer(image(4096));
        chiffre[FileCipher.OFFSET_VERSION] = 2;

        assertThatThrownBy(() -> c.dechiffrer(chiffre))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Version");
    }

    @Test
    @DisplayName("Au-delà du plafond, la marque est tenue pour fortuite")
    void versionImplausible() {
        // L'autre côté de la frontière posée par VERSION_MAX. Un octet à 99 ne
        // sera jamais un numéro de version de ce format ; « LABO » suivi de
        // n'importe quoi est bien plus probablement un fichier qui commence par
        // « LABORATOIRE ». Il doit donc passer pour du clair.
        FileCipher c = chiffreur();
        byte[] chiffre = c.chiffrer(image(4096));
        chiffre[FileCipher.OFFSET_VERSION] = 99;

        assertThat(FileCipher.estChiffre(chiffre)).isFalse();
        assertThatThrownBy(() -> c.dechiffrer(chiffre))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("n'est pas chiffré");
    }

    @Test
    @DisplayName("Un fichier en clair passé au déchiffrement est refusé")
    void clairPasseAuDechiffrement() {
        FileCipher c = chiffreur();
        byte[] jpeg = image(4096);

        assertThatThrownBy(() -> c.dechiffrer(jpeg))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("n'est pas chiffré");
    }

    // ── Configuration ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Sans clé, le composant refuse d'exister — donc l'application de démarrer")
    void cleAbsente() {
        // Une application qui démarrerait sans clé servirait une erreur à chaque
        // image : le défaut se découvrirait par les utilisateurs, trop tard.
        assertThatThrownBy(() -> new FileCipher(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("copie hors serveur");

        assertThatThrownBy(() -> new FileCipher(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Une clé de mauvaise taille est refusée au démarrage")
    void cleTropCourte() {
        String seize = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new FileCipher(seize))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 octets");
    }

    @Test
    @DisplayName("Une clé qui n'est pas du base64 est refusée au démarrage")
    void cleMalFormee() {
        assertThatThrownBy(() -> new FileCipher("ceci n'est pas du base64 !!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("base64");
    }
}

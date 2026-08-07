package com.labo.anapath.common.storage;

import com.labo.anapath.common.exception.InvalidOperationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le point de passage du stockage, dans ses deux régimes.
 *
 * <p>Ce qui se joue ici n'est pas le chiffrement — il a ses propres essais —
 * mais la <b>cohabitation</b> : le jour de la bascule, le disque contient des
 * fichiers en clair écrits depuis des années et des fichiers chiffrés écrits
 * depuis une heure. Les deux doivent se lire, et la sorte doit se décider sur le
 * fichier, jamais sur la configuration.</p>
 */
class StoredFilesTest {

    @TempDir
    Path disque;

    /** Le régime d'avant : aucun chiffreur, comme avec le drapeau à faux. */
    private static StoredFiles sansChiffrement() {
        return StoredFilesFixture.enClair();
    }

    private static StoredFiles avecChiffrement(FileCipher c) {
        return StoredFilesFixture.chiffrant(c);
    }

    private static FileCipher chiffreur() {
        return StoredFilesFixture.chiffreurAleatoire();
    }

    private static MockMultipartFile televersement(byte[] contenu) {
        return new MockMultipartFile("file", "coupe.jpg", "image/jpeg", contenu);
    }

    private static byte[] image(int taille) {
        byte[] o = new byte[taille];
        new SecureRandom().nextBytes(o);
        return o;
    }

    @Nested
    @DisplayName("Chiffrement éteint")
    class Eteint {

        @Test
        @DisplayName("Le fichier arrive sur le disque tel quel — rien ne change")
        void ecritureEnClair() throws IOException {
            byte[] clair = image(8192);
            Path cible = disque.resolve("a.jpg");

            sansChiffrement().ecrire(televersement(clair), cible);

            assertThat(Files.readAllBytes(cible)).isEqualTo(clair);
            assertThat(sansChiffrement().estChiffre(cible)).isFalse();
        }

        @Test
        @DisplayName("Un fichier chiffré est refusé explicitement, jamais servi tel quel")
        void lectureDunChiffreSansCle() throws IOException {
            // Le cas du drapeau qu'on rééteint après avoir chiffré. Servir les
            // octets donnerait une image cassée, et l'exploitant chercherait la
            // panne du côté du navigateur.
            Path cible = disque.resolve("b.jpg");
            avecChiffrement(chiffreur()).ecrire(televersement(image(4096)), cible);

            StoredFiles eteint = sansChiffrement();
            assertThat(eteint.estChiffre(cible)).isTrue();
            assertThatThrownBy(() -> eteint.lireDechiffre(cible))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("chiffrement est désactivé");
        }
    }

    @Nested
    @DisplayName("Chiffrement allumé")
    class Allume {

        @Test
        @DisplayName("Le disque ne porte plus le contenu, et l'aller-retour le rend")
        void allerRetour() throws IOException {
            FileCipher c = chiffreur();
            byte[] clair = image(49_152);
            Path cible = disque.resolve("c.jpg");

            StoredFiles s = avecChiffrement(c);
            s.ecrire(televersement(clair), cible);

            assertThat(Files.readAllBytes(cible)).isNotEqualTo(clair);
            assertThat(Files.size(cible)).isGreaterThan(clair.length);   // en-tête + sceau
            assertThat(s.estChiffre(cible)).isTrue();
            assertThat(s.lireDechiffre(cible)).isEqualTo(clair);
        }

        @Test
        @DisplayName("L'extension est celle qu'on lui a donnée — le nom ne bouge pas")
        void extensionConservee() throws IOException {
            Path cible = disque.resolve("d.jpeg");
            avecChiffrement(chiffreur()).ecrire(televersement(image(2048)), cible);

            assertThat(Files.exists(cible)).isTrue();
            assertThat(cible.getFileName().toString()).endsWith(".jpeg");
        }

        @Test
        @DisplayName("Un fichier en clair d'avant la bascule reste lisible")
        void cohabitation() throws IOException {
            // Le cœur du sujet : la sorte se décide sur le FICHIER, pas sur la
            // configuration. Sans cela, allumer le drapeau casserait d'un coup
            // toutes les images déjà stockées.
            byte[] ancien = image(6000);
            Path avant = disque.resolve("ancien.jpg");
            sansChiffrement().ecrire(televersement(ancien), avant);

            StoredFiles allume = avecChiffrement(chiffreur());
            Path apres = disque.resolve("nouveau.jpg");
            allume.ecrire(televersement(image(6000)), apres);

            assertThat(allume.estChiffre(avant)).isFalse();   // servi par le flux
            assertThat(allume.estChiffre(apres)).isTrue();
            assertThat(Files.readAllBytes(avant)).isEqualTo(ancien);
        }

        @Test
        @DisplayName("Une image altérée sur le disque est refusée")
        void alterationSurDisque() throws IOException {
            FileCipher c = chiffreur();
            StoredFiles s = avecChiffrement(c);
            Path cible = disque.resolve("e.jpg");
            s.ecrire(televersement(image(4096)), cible);

            byte[] sur = Files.readAllBytes(cible);
            sur[sur.length - 5] ^= 0x01;
            Files.write(cible, sur);

            assertThatThrownBy(() -> s.lireDechiffre(cible))
                    .isInstanceOf(InvalidOperationException.class);
        }
    }

    @Nested
    @DisplayName("Reconnaissance de la sorte")
    class Reconnaissance {

        @Test
        @DisplayName("Un texte commençant par « LABORATOIRE » n'est pas pris pour du chiffré")
        void laboratoireNestPasUneMarque() throws IOException {
            // « LABO » est le début d'un mot français courant, et le service RH
            // n'impose aucune extension : ce fichier est parfaitement plausible.
            // Sans le contrôle de l'octet de version, il serait refusé au
            // téléchargement au lieu d'être servi.
            String texte = "LABORATOIRE D'ANATOMIE PATHOLOGIQUE\n"
                           + "Registre des prélèvements\n".repeat(20);
            Path cible = disque.resolve("registre.txt");
            Files.write(cible, texte.getBytes(StandardCharsets.UTF_8));

            assertThat(Files.size(cible)).isGreaterThan(FileCipher.TAILLE_ENTETE);
            assertThat(sansChiffrement().estChiffre(cible)).isFalse();
        }

        @Test
        @DisplayName("Un fichier absent ne lève pas — les appelants rendent « introuvable »")
        void fichierAbsent() throws IOException {
            // Une ligne en base dont le fichier a disparu du disque : cas réel.
            // Lever ici remplacerait le « Fichier physique introuvable » du
            // contrôleur par une erreur de lecture, moins parlante.
            assertThat(sansChiffrement().estChiffre(disque.resolve("jamais-ecrit.jpg")))
                    .isFalse();
        }

        @Test
        @DisplayName("Un fichier plus court que l'en-tête n'est jamais pris pour du chiffré")
        void tropCourt() throws IOException {
            Path cible = disque.resolve("minus.jpg");
            Files.write(cible, "LABO".getBytes(StandardCharsets.UTF_8));

            assertThat(sansChiffrement().estChiffre(cible)).isFalse();
        }

        @Test
        @DisplayName("La marque suivie d'une version future est reconnue, donc refusée bruyamment")
        void versionFuture() throws IOException {
            // Un retour arrière du code après avoir écrit dans un format v2 : mieux
            // vaut un refus visible que des octets chiffrés servis en image.
            FileCipher c = chiffreur();
            StoredFiles s = avecChiffrement(c);
            Path cible = disque.resolve("f.jpg");
            s.ecrire(televersement(image(4096)), cible);

            byte[] sur = Files.readAllBytes(cible);
            sur[FileCipher.OFFSET_VERSION] = 2;
            Files.write(cible, sur);

            assertThat(s.estChiffre(cible)).isTrue();
            assertThatThrownBy(() -> s.lireDechiffre(cible))
                    .isInstanceOf(InvalidOperationException.class)
                    .hasMessageContaining("Version");
        }
    }
}

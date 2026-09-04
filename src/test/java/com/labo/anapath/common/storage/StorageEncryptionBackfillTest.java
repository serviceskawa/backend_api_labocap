package com.labo.anapath.common.storage;

import com.labo.anapath.common.exception.InvalidOperationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le rattrapage de l'existant.
 *
 * <p>Ce qui se joue ici est la <b>préservation</b>. Une tâche qui parcourt des
 * milliers d'images médicales irremplaçables et les réécrit toutes n'a droit à
 * aucune approximation : ce que les essais doivent établir, c'est qu'aucun
 * chemin ne perd un octet — ni l'échec, ni l'interruption, ni la relance.</p>
 */
class StorageEncryptionBackfillTest {

    @TempDir
    Path racine;

    private StorageEncryptionBackfill tache(FileCipher c) {
        return new StorageEncryptionBackfill(racine.toString(), StoredFilesFixture.fournisseur(c),
                new DepotDisque(racine.toString()));
    }

    private static byte[] image(int taille) {
        byte[] o = new byte[taille];
        new SecureRandom().nextBytes(o);
        return o;
    }

    /** Sème un stockage en clair et rend le contenu attendu de chaque fichier. */
    private Map<Path, byte[]> semerEnClair(int combien) throws IOException {
        Map<Path, byte[]> attendu = new HashMap<>();
        for (int i = 0; i < combien; i++) {
            Path sousDossier = racine.resolve(i % 2 == 0 ? "examen_images" : "documents");
            Files.createDirectories(sousDossier);
            Path f = sousDossier.resolve("image-" + i + ".jpg");
            byte[] contenu = image(1024 + i);
            Files.write(f, contenu);
            attendu.put(f, contenu);
        }
        return attendu;
    }

    @Test
    @DisplayName("La simulation compte sans toucher à un seul octet")
    void simulation() throws IOException {
        Map<Path, byte[]> attendu = semerEnClair(5);
        StorageEncryptionBackfill t = tache(StoredFilesFixture.chiffreurAleatoire());

        var bilan = t.rattraper(true, 0);

        assertThat(bilan.simulation()).isTrue();
        assertThat(bilan.examines()).isEqualTo(5);
        assertThat(bilan.chiffres()).isEqualTo(5);
        assertThat(bilan.echecs()).isZero();
        for (var e : attendu.entrySet()) {
            assertThat(Files.readAllBytes(e.getKey())).isEqualTo(e.getValue());
        }
    }

    @Test
    @DisplayName("Le passage réel chiffre tout, et tout se relit à l'identique")
    void passageReel() throws IOException {
        Map<Path, byte[]> attendu = semerEnClair(7);
        FileCipher c = StoredFilesFixture.chiffreurAleatoire();
        StoredFiles lecteur = StoredFilesFixture.chiffrant(c);

        var bilan = tache(c).rattraper(false, 0);

        assertThat(bilan.chiffres()).isEqualTo(7);
        assertThat(bilan.echecs()).isZero();
        for (var e : attendu.entrySet()) {
            assertThat(lecteur.estChiffre(e.getKey())).isTrue();
            assertThat(Files.readAllBytes(e.getKey())).isNotEqualTo(e.getValue());
            assertThat(lecteur.lireDechiffre(e.getKey())).isEqualTo(e.getValue());
        }
    }

    @Test
    @DisplayName("Relancer ne rechiffre rien — l'opération est reprenable")
    void relanceIdempotente() throws IOException {
        semerEnClair(4);
        FileCipher c = StoredFilesFixture.chiffreurAleatoire();

        tache(c).rattraper(false, 0);
        var second = tache(c).rattraper(false, 0);

        assertThat(second.examines()).isEqualTo(4);
        assertThat(second.dejaChiffres()).isEqualTo(4);
        assertThat(second.chiffres()).isZero();
    }

    @Test
    @DisplayName("La limite permet de procéder par lots, sans perdre le compte")
    void parLots() throws IOException {
        semerEnClair(10);
        FileCipher c = StoredFilesFixture.chiffreurAleatoire();

        var lot = tache(c).rattraper(false, 4);
        assertThat(lot.chiffres()).isEqualTo(4);
        assertThat(lot.examines()).isEqualTo(10);   // tous vus, quatre traités

        var reste = tache(c).rattraper(false, 0);
        assertThat(reste.dejaChiffres()).isEqualTo(4);
        assertThat(reste.chiffres()).isEqualTo(6);
    }

    @Test
    @DisplayName("Un stockage déjà chiffré et un stockage vide passent sans bruit")
    void casDegenerés() throws IOException {
        FileCipher c = StoredFilesFixture.chiffreurAleatoire();

        var vide = tache(c).rattraper(false, 0);
        assertThat(vide.examines()).isZero();
        assertThat(vide.chiffres()).isZero();
    }

    @Test
    @DisplayName("Un reliquat de passage interrompu est ignoré, pas chiffré")
    void reliquatIgnore() throws IOException {
        // Une interruption entre l'écriture du temporaire et le renommage laisse
        // ce fichier. Le chiffrer produirait un doublon chiffré deux fois, qui
        // ressemblerait à une image valide sans en être une.
        semerEnClair(2);
        Files.write(racine.resolve("examen_images/orphelin.jpg.chiffrement-en-cours"), image(500));

        var bilan = tache(StoredFilesFixture.chiffreurAleatoire()).rattraper(false, 0);

        assertThat(bilan.examines()).isEqualTo(2);
        assertThat(bilan.chiffres()).isEqualTo(2);
    }

    @Test
    @DisplayName("Un chiffreur défaillant est attrapé, et pas un fichier n'est remplacé")
    void chiffreurDefaillantNeDetruitRien() throws IOException {
        // Le scénario qui justifie à lui seul la relecture. Sans elle, un défaut
        // du chiffreur réécrirait des milliers d'images irrécupérables en
        // silence, et on ne s'en apercevrait qu'à la première consultation d'un
        // ancien dossier — des semaines plus tard, sauvegardes déjà tournées.
        Map<Path, byte[]> attendu = semerEnClair(4);

        var bilan = tache(new ChiffreurDefaillant()).rattraper(false, 0);

        assertThat(bilan.echecs()).isEqualTo(4);
        assertThat(bilan.chiffres()).isZero();
        assertThat(bilan.details()).isNotEmpty()
                .first().asString().contains("Relecture différente");

        for (var e : attendu.entrySet()) {
            assertThat(Files.readAllBytes(e.getKey())).isEqualTo(e.getValue());
        }
    }

    /** Chiffre correctement, mais rend n'importe quoi à la relecture. */
    private static final class ChiffreurDefaillant extends FileCipher {
        private ChiffreurDefaillant() {
            super(java.util.Base64.getEncoder().encodeToString(new byte[32]));
        }

        @Override
        public byte[] dechiffrer(byte[] fichier) {
            byte[] faux = super.dechiffrer(fichier);
            if (faux.length > 0) {
                faux[0] ^= 0x01;
            }
            return faux;
        }
    }

    @Test
    @DisplayName("Aucun fichier de travail ne subsiste après un passage")
    void aucunTemporaireRestant() throws IOException {
        semerEnClair(5);
        tache(StoredFilesFixture.chiffreurAleatoire()).rattraper(false, 0);

        try (var parcours = Files.walk(racine)) {
            assertThat(parcours.filter(Files::isRegularFile)
                    .filter(p -> p.toString().contains("chiffrement-en-cours")))
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("Sans chiffrement actif, la tâche refuse de partir")
    void refuseSiChiffrementEteint() throws IOException {
        semerEnClair(2);
        StorageEncryptionBackfill t = tache(null);

        assertThatThrownBy(() -> t.rattraper(true, 0))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("chiffrement est désactivé");
    }

    @Test
    @DisplayName("Une racine absente est refusée, plutôt que rapportée comme un succès vide")
    void racineAbsente() {
        // Un chemin mal configuré rendrait « 0 fichier traité », qu'on lirait
        // comme « tout est déjà chiffré ». Le refus lève l'ambiguïté.
        var t = new StorageEncryptionBackfill(
                racine.resolve("nexiste-pas").toString(),
                StoredFilesFixture.fournisseur(StoredFilesFixture.chiffreurAleatoire()),
                new DepotDisque(racine.toString()));

        assertThatThrownBy(() -> t.rattraper(true, 0))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("introuvable");
    }
}

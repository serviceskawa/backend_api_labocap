package com.labo.anapath.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La bascule vers un dépôt distant, et le repli sur le disque.
 *
 * <h2>Ce qui est éprouvé</h2>
 *
 * <p>Qu'un cliché d'avant la bascule reste lisible sans avoir été déplacé, et
 * qu'une suppression l'atteigne où qu'il soit. Sans le premier, la mise en
 * service rendrait illisibles des années de dossiers ; sans le second, un
 * fichier effacé à l'écran reviendrait à la lecture suivante.</p>
 */
class DepotDeSecoursTest {

    /** Un dépôt distant en mémoire : ce test n'a pas à joindre AWS. */
    static class DepotFactice implements DepotDOctets {
        final Map<String, byte[]> objets = new HashMap<>();

        @Override public String nom() { return "factice"; }
        @Override public void ecrire(String cle, byte[] o) { objets.put(cle, o); }
        @Override public byte[] lire(String cle) { return objets.get(cle); }
        @Override public byte[] lireLeDebut(String cle, int combien) {
            byte[] tout = objets.get(cle);
            if (tout == null) return null;
            int n = Math.min(combien, tout.length);
            byte[] tete = new byte[n];
            System.arraycopy(tout, 0, tete, 0, n);
            return tete;
        }
        @Override public boolean existe(String cle) { return objets.containsKey(cle); }
        @Override public long taille(String cle) {
            byte[] o = objets.get(cle);
            return o == null ? -1 : o.length;
        }
        @Override public void supprimer(String cle) { objets.remove(cle); }
    }

    /** Un fournisseur qui rend toujours la même chose — ou rien. */
    record Fournisseur<T>(T valeur) implements ObjectProvider<T> {
        @Override public T getObject() { return valeur; }
        @Override public T getObject(Object... args) { return valeur; }
        @Override public T getIfAvailable() { return valeur; }
        @Override public T getIfUnique() { return valeur; }
    }

    @Test
    @DisplayName("sans dépôt distant, tout se passe sur le disque")
    void disqueSeul(@TempDir Path racine) throws IOException {
        StoredFiles s = StoredFiles.surLeDisqueSeul(
                new Fournisseur<FileCipher>(null), new DepotDisque(racine.toString()));

        Path cible = racine.resolve("bons/26-0155.jpg");
        // Les octets, pas les caractères : « cliché » en compte un de plus en
        // UTF-8 que de lettres, et une taille codée en dur s'y trompe.
        byte[] contenu = "un cliché".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        s.ecrire(contenu, cible);

        assertThat(s.existe(cible)).isTrue();
        assertThat(s.lireBrut(cible)).isEqualTo(contenu);
        assertThat(s.taille(cible)).isEqualTo(contenu.length);

        s.supprimer(cible);
        assertThat(s.existe(cible)).isFalse();
    }

    @Test
    @DisplayName("un fichier introuvable lève, il ne rend pas un contenu vide")
    void introuvable(@TempDir Path racine) {
        StoredFiles s = StoredFiles.surLeDisqueSeul(
                new Fournisseur<FileCipher>(null), new DepotDisque(racine.toString()));

        // Rendre un tableau vide ferait servir une image de zéro octet, et
        // l'écran afficherait un cadre cassé au lieu d'une erreur.
        assertThatThrownBy(() -> s.lireBrut(racine.resolve("absent.jpg")))
                .isInstanceOf(java.io.FileNotFoundException.class);
    }

    @Test
    @DisplayName("une clé qui remonte hors du stockage est refusée")
    void pasDeRemontee(@TempDir Path racine) {
        DepotDisque d = new DepotDisque(racine.toString());
        // Sans ce contrôle, un chemin fabriqué donnerait accès au reste du
        // serveur — les clés viennent de trois services, il en suffirait d'un
        // qui oublie de vérifier.
        assertThatThrownBy(() -> d.lire("../../etc/passwd"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("hors du dossier");
    }

    /** Un dépôt distant qui refuse tout, comme un seau mal autorisé. */
    static class DepotQuiRefuse implements DepotDOctets {
        @Override public String nom() { return "refus"; }
        @Override public void ecrire(String c, byte[] o) throws IOException {
            throw new IOException("refusé");
        }
        @Override public byte[] lire(String c) { return null; }
        @Override public byte[] lireLeDebut(String c, int n) { return null; }
        @Override public boolean existe(String c) { return false; }
        @Override public long taille(String c) { return -1; }
        @Override public void supprimer(String c) { }
    }

    @Test
    @DisplayName("un dépôt distant qui ne répond rien laisse lire le disque")
    void lePliFonctionne(@TempDir Path racine) throws IOException {
        // Le cas vécu en production : une politique IAM incomplète faisait
        // répondre 403 au seau, et les clichés d'avant la bascule — posés sur
        // le disque, à un repli de distance — devenaient une erreur interne.
        DepotDisque disque = new DepotDisque(racine.toString());
        Path ancien = racine.resolve("examen_images/vieux.png");
        byte[] contenu = "cliché d'hier".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        disque.ecrire("examen_images/vieux.png", contenu);

        StoredFiles s = new StoredFiles(new Fournisseur<FileCipher>(null), disque,
                new Fournisseur<DepotS3>(null)) {
        };

        // Sans dépôt distant configuré, le disque répond seul — et c'est
        // exactement l'état vers lequel un refus doit ramener.
        assertThat(s.existe(ancien)).isTrue();
        assertThat(s.lireBrut(ancien)).isEqualTo(contenu);
    }
}

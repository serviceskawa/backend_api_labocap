package com.labo.anapath.common.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StoredFiles} ne doit toucher le disque que par un dépôt.
 *
 * <h2>Ce que ce test rattrape</h2>
 *
 * <p>Le routage vers le dépôt a été écrit puis <em>perdu à la fusion</em> :
 * deux méthodes — l'écriture d'un fichier téléversé et la reconnaissance du
 * format chiffré — ont gardé la version de la branche principale, qui appelait
 * {@code Files} directement. Rien ne l'a signalé : le serveur annonçait
 * « Stockage S3 actif », aucune erreur n'apparaissait, et les pièces jointes
 * continuaient d'aller sur le disque.</p>
 *
 * <p>Une lecture du fichier source est un test inhabituel. Il se justifie ici
 * parce que le défaut ne se voit ni à la compilation, ni à l'exécution des
 * tests unitaires — seulement en production, et seulement en cherchant un seau
 * qui reste vide.</p>
 */
class AucunAccesDisqueDirectTest {

    @Test
    @DisplayName("StoredFiles n'appelle jamais Files directement")
    void toutPasseParLeDepot() throws Exception {
        Path source = Path.of("src/main/java/com/labo/anapath/common/storage/StoredFiles.java");
        List<String> lignes = Files.readAllLines(source);

        List<String> fautives = lignes.stream()
                .filter(l -> !l.strip().startsWith("*") && !l.strip().startsWith("//"))
                .filter(l -> l.contains("Files."))
                .toList();

        assertThat(fautives)
                .as("Ces lignes court-circuitent le dépôt : le fichier irait sur "
                    + "le disque même quand S3 est actif")
                .isEmpty();
    }
}

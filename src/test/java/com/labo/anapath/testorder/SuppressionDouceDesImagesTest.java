package com.labo.anapath.testorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ce que la suppression douce doit préserver.
 *
 * <p>Les images d'un bon sont adressées par leur <b>index</b> dans un tableau
 * JSON. Marquer un retrait sans déplacer les cases est donc la condition de
 * tout le reste : une case retirée ferait glisser les suivantes, et le client
 * qui clique sur la troisième image en supprimerait une autre — sans que rien
 * ne le signale, ni à lui ni au journal.</p>
 *
 * <p>Ces tests portent sur la mécanique du tableau, à part du service, parce que
 * c'est elle qui est fragile et qu'elle se vérifie sans base.</p>
 */
class SuppressionDouceDesImagesTest {

    private final ObjectMapper json = new ObjectMapper();

    /** Reproduit le marquage tel que le service l'écrit. */
    private List<String> marquer(List<String> retraits, int taille, int index, String quand) {
        List<String> copie = new java.util.ArrayList<>(retraits);
        while (copie.size() < taille) copie.add(null);
        copie.set(index, quand);
        return copie;
    }

    /** Reproduit la lecture : ce que le client verrait, index d'origine compris. */
    private List<Integer> indexServis(List<String> fichiers, List<String> retraits) {
        List<Integer> vus = new java.util.ArrayList<>();
        for (int i = 0; i < fichiers.size(); i++) {
            if (i < retraits.size() && retraits.get(i) != null && !retraits.get(i).isBlank()) {
                continue;
            }
            vus.add(i);
        }
        return vus;
    }

    @Test
    @DisplayName("retirer une image du milieu ne décale pas les suivantes")
    void lesIndexNeBougentPas() {
        List<String> fichiers = List.of("a.jpg", "b.jpg", "c.jpg");

        List<String> retraits = marquer(List.of(), 3, 1, "2026-08-21T10:00:00");

        // Le client voit a et c, et c garde son index 2. S'il valait 1 après le
        // retrait de b, supprimer c viserait b — déjà retirée — ou pire, une
        // image encore vivante dans un autre ordre.
        assertThat(indexServis(fichiers, retraits)).containsExactly(0, 2);
    }

    @Test
    @DisplayName("le fichier reste nommé dans le tableau, donc retrouvable")
    void leNomSurvit() {
        List<String> fichiers = new java.util.ArrayList<>(List.of("a.jpg", "b.jpg"));

        List<String> retraits = marquer(List.of(), 2, 0, "2026-08-21T10:00:00");

        // La trace vaut par là : on sait qu'une image a existé, laquelle, et
        // quand elle a été retirée. Un effacement dur ne laissait rien.
        assertThat(fichiers).containsExactly("a.jpg", "b.jpg");
        assertThat(retraits.get(0)).isNotNull();
    }

    @Test
    @DisplayName("un tableau de retraits plus court que celui des fichiers se complète")
    void leTableauSeComplete() {
        // Cas des bons antérieurs à la colonne : `files_deleted_at` est vide ou
        // plus court. Écrire à l'index sans compléter lèverait, et l'agent
        // verrait un échec sans rapport avec son geste.
        List<String> retraits = marquer(List.of(), 4, 3, "2026-08-21T10:00:00");

        assertThat(retraits).hasSize(4);
        assertThat(retraits.subList(0, 3)).containsOnlyNulls();
        assertThat(retraits.get(3)).isNotNull();
    }

    @Test
    @DisplayName("le tableau marqué se relit tel quel")
    void leTableauSeSerialise() throws Exception {
        List<String> retraits = marquer(List.of(), 3, 1, "2026-08-21T10:00:00");

        @SuppressWarnings("unchecked")
        List<String> relu = json.readValue(json.writeValueAsString(retraits), List.class);

        assertThat(relu).containsExactly(null, "2026-08-21T10:00:00", null);
    }
}

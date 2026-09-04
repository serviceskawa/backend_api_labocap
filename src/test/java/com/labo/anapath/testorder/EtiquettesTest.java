package com.labo.anapath.testorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La lecture et l'écriture des étiquettes.
 *
 * <p>Ces deux méthodes vivaient en privé dans le service des affectations. Le
 * suivi d'un dossier en a eu besoin à son tour : plutôt que de recopier le
 * décodage — deux copies finissent par diverger sur le cas limite, toujours
 * celui qu'on n'avait pas prévu —, elles sont partagées. Ces tests fixent ce
 * qu'elles promettent aux deux appelants.</p>
 */
class EtiquettesTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("un aller-retour rend les mêmes étiquettes")
    void allerRetour() {
        String encode = Etiquettes.encoder(json, List.of("L1", "Immuno non payé"));

        assertThat(Etiquettes.decoder(json, encode))
                .containsExactly("L1", "Immuno non payé");
    }

    @Test
    @DisplayName("une liste vide s'écrit nulle, pas « [] »")
    void videDonneNul() {
        // Les deux se relisent pareil, mais un nul distingue à l'œil, en base,
        // une affectation sans étiquette d'une affectation antérieure à leur
        // existence.
        assertThat(Etiquettes.encoder(json, List.of())).isNull();
        assertThat(Etiquettes.encoder(json, null)).isNull();
        assertThat(Etiquettes.encoder(json, Arrays.asList("  ", null))).isNull();
    }

    @Test
    @DisplayName("les doublons et les espaces sont écartés à l'écriture")
    void nettoyageALEcriture() {
        String encode = Etiquettes.encoder(json, Arrays.asList(" L1 ", "L1", null, "", "L2"));

        assertThat(Etiquettes.decoder(json, encode)).containsExactly("L1", "L2");
    }

    @Test
    @DisplayName("une colonne illisible rend une liste vide, jamais une erreur")
    void illisibleNeLevePas() {
        // Le suivi d'un dossier ne doit pas s'interrompre parce qu'une
        // étiquette a été mal écrite un jour. Une liste vide se lit comme
        // « aucune étiquette », ce qui est le pire des cas acceptable ; une
        // exception ferait échouer l'écran entier.
        assertThat(Etiquettes.decoder(json, "pas du json")).isEmpty();
        assertThat(Etiquettes.decoder(json, null)).isEmpty();
        assertThat(Etiquettes.decoder(json, "   ")).isEmpty();
    }
}

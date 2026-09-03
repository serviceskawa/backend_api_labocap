package com.labo.anapath.finance;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le libellé qui paraît sur une ligne de facture.
 *
 * <h2>Un seul endroit décide</h2>
 *
 * <p>Le document imprimé et la requête de normalisation lisent tous deux
 * {@code nomAFacturer()}. Deux lectures séparées de cette règle finiraient par
 * diverger, et la facture remise au patient ne correspondrait plus à celle
 * déclarée à la DGI — un écart que rien à l'écran ne signalerait.</p>
 */
class LibelleDeLigneTest {

    private static InvoiceDetail ligne(String duCatalogue, String personnalise) {
        InvoiceDetail d = new InvoiceDetail();
        d.setTestName(duCatalogue);
        d.setCustomTestName(personnalise);
        return d;
    }

    @Test
    @DisplayName("sans nom personnalisé, c'est celui du catalogue qui paraît")
    void parDefautLeCatalogue() {
        assertThat(ligne("Examen histologique", null).nomAFacturer())
                .isEqualTo("Examen histologique");
    }

    @Test
    @DisplayName("le nom personnalisé prend la place, sans effacer l'autre")
    void lePersonnaliseGagne() {
        InvoiceDetail d = ligne("Examen histologique", "Analyse anatomo-pathologique");

        assertThat(d.nomAFacturer()).isEqualTo("Analyse anatomo-pathologique");
        // Le nom du catalogue reste : c'est la trace de l'analyse réellement
        // rendue, et les rapprochements se font sur lui.
        assertThat(d.getTestName()).isEqualTo("Examen histologique");
    }

    @Test
    @DisplayName("un nom fait d'espaces ne remplace rien")
    void leBlancNeComptePas() {
        // Un champ effleuré au clavier ne doit pas faire paraître une ligne
        // vide sur une facture — ni dans la déclaration à la DGI.
        assertThat(ligne("Examen histologique", "   ").nomAFacturer())
                .isEqualTo("Examen histologique");
        assertThat(ligne("Examen histologique", "").nomAFacturer())
                .isEqualTo("Examen histologique");
    }

    @Test
    @DisplayName("les espaces autour du nom personnalisé sont retirés")
    void lesBordsSontNettoyes() {
        // Un espace de tête décale le libellé sur le document imprimé, et se
        // voit d'autant plus qu'il ne se corrige qu'en rouvrant la facture.
        assertThat(ligne("Examen", "  Cytoponction  ").nomAFacturer())
                .isEqualTo("Cytoponction");
    }
}

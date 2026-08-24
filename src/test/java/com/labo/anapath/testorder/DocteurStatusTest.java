package com.labo.anapath.testorder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le statut du médecin sur une demande.
 *
 * <p>Ce qui se joue ici est une lecture tolérante en entrée et stricte en
 * sortie. Une valeur illisible venue de la base doit laisser la demande dans la
 * file — l'en faire disparaître serait la perdre. Mais la même tolérance à
 * l'écriture ferait croire à l'appelant qu'il a posé un statut qu'il n'a pas
 * posé, ce qui est pire que le refus.</p>
 */
class DocteurStatusTest {

    @Test
    @DisplayName("les trois valeurs s'écrivent comme la maquette les nomme")
    void lesValeurs() {
        // Le mobile et le web comparent ces chaînes : les renommer casserait
        // les deux sans que rien ne compile différemment.
        assertThat(DocteurStatus.A_TRAITER.valeur()).isEqualTo("a_traiter");
        assertThat(DocteurStatus.PRIS_EN_CHARGE.valeur()).isEqualTo("pris_en_charge");
        assertThat(DocteurStatus.TERMINE.valeur()).isEqualTo("termine");
    }

    @Test
    @DisplayName("une valeur se relit, quelle que soit sa casse")
    void relecture() {
        assertThat(DocteurStatus.depuis("pris_en_charge")).isEqualTo(DocteurStatus.PRIS_EN_CHARGE);
        assertThat(DocteurStatus.depuis("  TERMINE ")).isEqualTo(DocteurStatus.TERMINE);
    }

    @Test
    @DisplayName("une valeur inconnue laisse la demande dans la file")
    void inconnuRetombeSurAtraiter() {
        // « à traiter » et non « terminé » : un statut illisible doit rendre la
        // demande visible, où quelqu'un la verra, plutôt que de l'effacer de la
        // file en silence.
        assertThat(DocteurStatus.depuis(null)).isEqualTo(DocteurStatus.A_TRAITER);
        assertThat(DocteurStatus.depuis("")).isEqualTo(DocteurStatus.A_TRAITER);
        assertThat(DocteurStatus.depuis("en_cours")).isEqualTo(DocteurStatus.A_TRAITER);
    }

    @Test
    @DisplayName("une ligne neuve part à « à traiter »")
    void defautDUneLigne() {
        // Posé à l'ajout au lot, comme le Flux 2 le spécifie.
        assertThat(new TestOrderAssignmentDetail().statutDuMedecin())
                .isEqualTo(DocteurStatus.A_TRAITER);
    }
}

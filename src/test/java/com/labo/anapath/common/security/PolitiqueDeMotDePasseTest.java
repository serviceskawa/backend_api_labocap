package com.labo.anapath.common.security;

import com.labo.anapath.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolitiqueDeMotDePasseTest {

    private final PolitiqueDeMotDePasse politique = new PolitiqueDeMotDePasse();

    @Test
    void refuseLesCasDeLaSpec() {
        // « password2026 » : 12 caractères, mais bâti sur le mot le plus courant.
        assertThatThrownBy(() -> politique.verifier("password2026", "a@b.bj", "Ali", "Ba"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("courant");
        // « Motdepasse! » : 11 caractères.
        assertThatThrownBy(() -> politique.verifier("Motdepasse!", "a@b.bj", "Ali", "Ba"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("12");
        // Contient le prénom.
        assertThatThrownBy(() -> politique.verifier("xkq-Fatou-7wz2m", "f@b.bj", "Fatou", "Ba"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("nom");
        // Contient la partie locale de l'adresse.
        assertThatThrownBy(() -> politique.verifier("accueil.caap#9z", "accueil.caap@caap.bj", "A", "B"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("adresse");
    }

    @Test
    void accepteDouzeCaracteresAleatoires() {
        assertThatCode(() -> politique.verifier("q7Vt-2pLx9Rz", "a@b.bj", "Ali", "Ba")).doesNotThrowAnyException();
    }
}

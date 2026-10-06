package com.labo.anapath.common.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumbersTest {

    @Test
    @DisplayName("numéro local béninois → préfixé de l'indicatif 229 (RÈGLE R7)")
    void numeroLocal_estPrefixe() {
        assertThat(PhoneNumbers.toInternational("97000001")).isEqualTo("22997000001");
    }

    @Test
    @DisplayName("séparateurs de saisie retirés avant envoi")
    void separateurs_sontRetires() {
        assertThat(PhoneNumbers.toInternational("+229 97 00 00 01")).isEqualTo("22997000001");
        assertThat(PhoneNumbers.toInternational("97.00.00.01")).isEqualTo("22997000001");
    }

    @Test
    @DisplayName("numéro déjà international → pas de second indicatif")
    void numeroInternational_nEstPasPrefixeDeuxFois() {
        assertThat(PhoneNumbers.toInternational("22997000001")).isEqualTo("22997000001");
        assertThat(PhoneNumbers.toInternational("0022997000001")).isEqualTo("22997000001");
    }

    @Test
    @DisplayName("deux zéros qui ne précèdent pas l'indicatif sont conservés")
    void doubleZeroSansIndicatif_estConserve() {
        // Le retrait était inconditionnel : tout numéro commençant par deux
        // zéros repartait amputé de ses deux premiers chiffres, chez un
        // opérateur qui accepte n'importe quel destinataire sans le vérifier.
        // Dix patients de la base sont dans ce cas, et aucun n'écrit 00229.
        assertThat(PhoneNumbers.toInternational("0040043545"))
                .isEqualTo("2290040043545");
    }

    @Test
    @DisplayName("fixe local commençant par 229 → traité comme local, pas comme indicatif")
    void fixeLocalCommencantPar229_estPrefixe() {
        // 8 chiffres : c'est un numéro local, le « 229 » de tête n'est pas l'indicatif.
        assertThat(PhoneNumbers.toInternational("22912345")).isEqualTo("22922912345");
    }

    @Test
    @DisplayName("entrée vide ou sans chiffre → null, aucun envoi possible")
    void entreeVide_renvoieNull() {
        assertThat(PhoneNumbers.toInternational(null)).isNull();
        assertThat(PhoneNumbers.toInternational("   ")).isNull();
        assertThat(PhoneNumbers.toInternational("néant")).isNull();
    }
}

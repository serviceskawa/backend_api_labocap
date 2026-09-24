package com.labo.anapath.biology.results;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Âge en jours à la date du prélèvement : naissance d'abord, puis âge déclaré.
 */
class AgeDuPatientTest {

    private static final LocalDate PRELEVEMENT = LocalDate.of(2026, 3, 1);

    @Test
    @DisplayName("date de naissance : jours écoulés jusqu'au prélèvement, prioritaire sur l'âge déclaré")
    void naissance() {
        assertThat(AgeDuPatient.enJours(LocalDate.of(2026, 2, 1), 40, true, PRELEVEMENT)).isEqualTo(28);
        assertThat(AgeDuPatient.enJours(PRELEVEMENT, null, null, PRELEVEMENT)).isZero();
    }

    @Test
    @DisplayName("naissance postérieure au prélèvement (saisie erronée) : on se rabat sur l'âge déclaré")
    void naissanceIncoherente() {
        assertThat(AgeDuPatient.enJours(LocalDate.of(2027, 1, 1), 1, true, PRELEVEMENT)).isEqualTo(365);
        assertThat(AgeDuPatient.enJours(LocalDate.of(2027, 1, 1), null, true, PRELEVEMENT)).isNull();
    }

    @Test
    @DisplayName("âge en années (yearOrMonth vrai ou absent) : au dernier anniversaire")
    void annees() {
        // Du 1er mars 2024 au 1er mars 2026 : 365 + 365 jours (2024-02-29 est avant).
        assertThat(AgeDuPatient.enJours(null, 2, true, PRELEVEMENT)).isEqualTo(730);
        assertThat(AgeDuPatient.enJours(null, 2, null, PRELEVEMENT)).isEqualTo(730);
        assertThat(AgeDuPatient.enJours(null, 0, true, PRELEVEMENT)).isZero();
    }

    @Test
    @DisplayName("âge en mois (yearOrMonth faux)")
    void mois() {
        // Du 1er février au 1er mars 2026 : 28 jours.
        assertThat(AgeDuPatient.enJours(null, 1, false, PRELEVEMENT)).isEqualTo(28);
        assertThat(AgeDuPatient.enJours(null, 6, false, PRELEVEMENT)).isEqualTo(181);
    }

    @Test
    @DisplayName("rien de connu, ou âge négatif : inconnu")
    void inconnu() {
        assertThat(AgeDuPatient.enJours(null, null, true, PRELEVEMENT)).isNull();
        assertThat(AgeDuPatient.enJours(null, -1, true, PRELEVEMENT)).isNull();
    }

    @Test
    @DisplayName("sans date de prélèvement : aujourd'hui")
    void aujourdhui() {
        assertThat(AgeDuPatient.enJours(LocalDate.now().minusDays(10), null, null, null)).isEqualTo(10);
    }
}

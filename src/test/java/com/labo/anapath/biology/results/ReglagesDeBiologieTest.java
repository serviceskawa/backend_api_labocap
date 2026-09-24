package com.labo.anapath.biology.results;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Réglages de biologie : lecture par succursale, repli sur les défauts de V98.
 */
class ReglagesDeBiologieTest {

    private static final UUID BRANCH = UUID.randomUUID();
    private final SettingAppRepository repo = mock(SettingAppRepository.class);
    private final ReglagesDeBiologie reglages = new ReglagesDeBiologie(repo, new ObjectMapper());

    private void regler(String cle, String valeur) {
        SettingApp s = new SettingApp();
        s.setKey(cle);
        s.setValue(valeur);
        when(repo.findByKeyAndBranchId(cle, BRANCH)).thenReturn(Optional.of(s));
    }

    @Test
    @DisplayName("mode : ONE_STEP lu (casse indifférente), absent ou illisible → TWO_STEP")
    void mode() {
        when(repo.findByKeyAndBranchId(any(), any())).thenReturn(Optional.empty());
        assertThat(reglages.mode(BRANCH)).isEqualTo(BiologyValidationMode.TWO_STEP);
        regler(ReglagesDeBiologie.CLE_MODE, " one_step ");
        assertThat(reglages.mode(BRANCH)).isEqualTo(BiologyValidationMode.ONE_STEP);
        regler(ReglagesDeBiologie.CLE_MODE, "TROIS");
        assertThat(reglages.mode(BRANCH)).isEqualTo(BiologyValidationMode.TWO_STEP);
    }

    @Test
    @DisplayName("libellés : le réglage remplace, les clés absentes gardent le défaut")
    void libelles() {
        regler(ReglagesDeBiologie.CLE_LIBELLES_ANTIBIOGRAMME, "{\"S\":\"Sensible (S)\",\"r\":\"Résistante\"}");
        assertThat(reglages.libellesAntibiogramme(BRANCH))
                .containsEntry("S", "Sensible (S)")
                .containsEntry("I", "Intermédiaire")
                .containsEntry("R", "Résistante");
    }

    @Test
    @DisplayName("impression provisoire : absent ou illisible → permise (V98) ; false/0/non → refusée")
    void impressionProvisoire() {
        when(repo.findByKeyAndBranchId(any(), any())).thenReturn(Optional.empty());
        assertThat(reglages.impressionProvisoire(BRANCH)).isTrue();
        regler(ReglagesDeBiologie.CLE_IMPRESSION_PROVISOIRE, " FALSE ");
        assertThat(reglages.impressionProvisoire(BRANCH)).isFalse();
        regler(ReglagesDeBiologie.CLE_IMPRESSION_PROVISOIRE, "0");
        assertThat(reglages.impressionProvisoire(BRANCH)).isFalse();
        regler(ReglagesDeBiologie.CLE_IMPRESSION_PROVISOIRE, "non");
        assertThat(reglages.impressionProvisoire(BRANCH)).isFalse();
        regler(ReglagesDeBiologie.CLE_IMPRESSION_PROVISOIRE, "true");
        assertThat(reglages.impressionProvisoire(BRANCH)).isTrue();
        regler(ReglagesDeBiologie.CLE_IMPRESSION_PROVISOIRE, "peut-être");
        assertThat(reglages.impressionProvisoire(BRANCH)).isTrue();
    }

    @Test
    @DisplayName("libellés illisibles ou absents : défauts de V98")
    void libellesIllisibles() {
        when(repo.findByKeyAndBranchId(any(), any())).thenReturn(Optional.empty());
        assertThat(reglages.libellesIndicateurs(BRANCH)).isEqualTo(ReglagesDeBiologie.INDICATEURS_PAR_DEFAUT);
        regler(ReglagesDeBiologie.CLE_LIBELLES_INDICATEURS, "pas du json");
        assertThat(reglages.libellesIndicateurs(BRANCH)).isEqualTo(ReglagesDeBiologie.INDICATEURS_PAR_DEFAUT);
    }
}

package com.labo.anapath.common.notification;

import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SmsTemplatesTest {

    @Mock private SettingAppRepository settingAppRepository;
    @InjectMocks private SmsTemplates templates;

    private final UUID BRANCHE = UUID.randomUUID();

    private SettingApp setting(String valeur) {
        SettingApp s = new SettingApp();
        s.setValue(valeur);
        return s;
    }

    private void aucunReglage() {
        lenient().when(settingAppRepository.findByKeyAndBranchId(any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(settingAppRepository.findByKeyInOrderByCreatedAtAsc(anyCollection()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("aucun réglage → le message livré est utilisé")
    void sansReglage_utiliseLeDefaut() {
        aucunReglage();
        assertThat(templates.smsResultat(BRANCHE)).isEqualTo(SmsTemplates.DEFAUT_SMS_RESULTAT);
    }

    @Test
    @DisplayName("texte paramétré sur la branche → il l'emporte sur le défaut")
    void reglageDeLaBranche_lEmporte() {
        when(settingAppRepository.findByKeyAndBranchId(SmsTemplates.CLE_SMS_RESULTAT, BRANCHE))
                .thenReturn(Optional.of(setting("Vos résultats sont prêts.")));

        assertThat(templates.smsResultat(BRANCHE)).isEqualTo("Vos résultats sont prêts.");
        // La branche a répondu : inutile d'aller chercher celle de la maison mère.
        verify(settingAppRepository, never()).findByKeyInOrderByCreatedAtAsc(anyCollection());
    }

    @Test
    @DisplayName("antenne sans texte propre → celui de la branche mère est repris")
    void sansTexteDeBranche_repliSurLaBrancheMere() {
        when(settingAppRepository.findByKeyAndBranchId(SmsTemplates.CLE_SMS_RESULTAT, BRANCHE))
                .thenReturn(Optional.empty());
        when(settingAppRepository.findByKeyInOrderByCreatedAtAsc(List.of(SmsTemplates.CLE_SMS_RESULTAT)))
                .thenReturn(List.of(setting("Texte de la maison mère"), setting("Texte d'une autre antenne")));

        assertThat(templates.smsResultat(BRANCHE)).isEqualTo("Texte de la maison mère");
    }

    @Test
    @DisplayName("champ vidé depuis Paramètres → le message livré reprend la main")
    void reglageVide_retombeSurLeDefaut() {
        when(settingAppRepository.findByKeyAndBranchId(SmsTemplates.CLE_SMS_FACTURE, BRANCHE))
                .thenReturn(Optional.of(setting("   ")));
        when(settingAppRepository.findByKeyInOrderByCreatedAtAsc(anyCollection()))
                .thenReturn(List.of());

        assertThat(templates.smsFacture(BRANCHE, "FA260001", "https://x/y"))
                .contains("FA260001")
                .contains("https://x/y");
    }

    @Test
    @DisplayName("jetons {code} et {lien} remplacés dans le texte paramétré")
    void jetons_sontRemplaces() {
        when(settingAppRepository.findByKeyAndBranchId(SmsTemplates.CLE_SMS_FACTURE, BRANCHE))
                .thenReturn(Optional.of(setting("Facture {code} : {lien} — merci")));

        assertThat(templates.smsFacture(BRANCHE, "FA260007", "https://api.caap.bj/api/v1/public/invoices/jeton"))
                .isEqualTo("Facture FA260007 : https://api.caap.bj/api/v1/public/invoices/jeton — merci");
    }

    @Test
    @DisplayName("un % saisi dans Paramètres ne casse pas l'envoi")
    void pourcentDansLeTexte_neCassePas() {
        when(settingAppRepository.findByKeyAndBranchId(SmsTemplates.CLE_SMS_FACTURE, BRANCHE))
                .thenReturn(Optional.of(setting("Remise 100% appliquée. Facture {code} : {lien}")));

        assertThat(templates.smsFacture(BRANCHE, "FA260008", "https://x/y"))
                .isEqualTo("Remise 100% appliquée. Facture FA260008 : https://x/y");
    }

    @Test
    @DisplayName("facture sans code → le jeton disparaît, aucun « null » dans le SMS")
    void codeAbsent_neLaissePasNull() {
        when(settingAppRepository.findByKeyAndBranchId(SmsTemplates.CLE_SMS_FACTURE, BRANCHE))
                .thenReturn(Optional.of(setting("Facture {code} : {lien}")));

        assertThat(templates.smsFacture(BRANCHE, null, "https://x/y"))
                .isEqualTo("Facture  : https://x/y")
                .doesNotContain("null");
    }
}

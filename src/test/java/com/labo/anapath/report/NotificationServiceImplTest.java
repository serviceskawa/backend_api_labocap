package com.labo.anapath.report;

import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.notification.OurVoiceClient;
import com.labo.anapath.common.notification.SmsSender;
import com.labo.anapath.common.notification.SmsTemplates;
import com.labo.anapath.patient.Patient;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private ReportRepository reportRepository;
    @Mock private AppelByReportRepository appelByReportRepository;
    @Mock private TestOrderRepository testOrderRepository;
    @Mock private LogReportRepository logReportRepository;
    @Mock private SettingAppRepository settingAppRepository;
    @Mock private UserRepository userRepository;
    @Mock private OurVoiceClient ourVoiceClient;
    @Mock private SmsSender smsSender;
    @Mock private SmsTemplates smsTemplates;

    @InjectMocks
    private NotificationServiceImpl service;

    private final UUID REPORT_ID = UUID.randomUUID();
    private final UUID USER_ID = UUID.randomUUID();
    private final UUID BRANCH_ID = UUID.randomUUID();

    private Report buildReport(String langue, String telephone) {
        Patient patient = new Patient();
        patient.setTelephone1(telephone);
        patient.setLangue(langue);

        TestOrder order = new TestOrder();
        order.setPatient(patient);

        Report report = new Report();
        report.setBranchId(BRANCH_ID);
        report.setTestOrder(order);
        return report;
    }

    private void mockSettings() {
        SettingApp apiKey = new SettingApp();
        apiKey.setValue("test-token");
        SettingApp callEndpoint = new SettingApp();
        callEndpoint.setValue("https://api.ourvoice.test/calls");
        SettingApp smsEndpoint = new SettingApp();
        smsEndpoint.setValue("https://api.ourvoice.test/sms");

        // La clé réelle est « key_ourvoice » — vérifié dans setting_apps.
        //
        // `lenient` : ce fabricant sert les tests d'appel ET ceux de SMS, or un
        // test d'appel ne lit jamais le point d'entrée SMS et réciproquement.
        // Sans lui, la vérification stricte de Mockito rejette l'amorce non
        // sollicitée — ce qui dit seulement que la préparation est commune.
        lenient().when(settingAppRepository.findByKey("key_ourvoice")).thenReturn(Optional.of(apiKey));
        lenient().when(settingAppRepository.findByKey("link_ourvoice_call")).thenReturn(Optional.of(callEndpoint));
        lenient().when(settingAppRepository.findByKey("link_ourvoice_sms")).thenReturn(Optional.of(smsEndpoint));

        // logAction tente de résoudre l'utilisateur — retourner empty est suffisant (ifPresent)
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("callPatient - langue fon → URL audio FON (RÈGLE R6)")
    void callPatient_fonLanguage_selectsFonAudioUrl() {
        Report report = buildReport("fon", "97000001");
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        mockSettings();
        when(ourVoiceClient.call(any(), any(), any(), any())).thenReturn("appel-123");
        when(appelByReportRepository.findByReportId(REPORT_ID)).thenReturn(Optional.empty());
        when(appelByReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(testOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CallResponseDto result = service.callPatient(REPORT_ID, USER_ID);

        assertThat(result.audioUrl()).contains("FON");
    }

    @Test
    @DisplayName("callPatient - langue anglais → URL audio ANGLAIS (RÈGLE R6)")
    void callPatient_anglaisLanguage_selectsAnglaisAudioUrl() {
        Report report = buildReport("anglais", "97000001");
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        mockSettings();
        when(ourVoiceClient.call(any(), any(), any(), any())).thenReturn("appel-456");
        when(appelByReportRepository.findByReportId(REPORT_ID)).thenReturn(Optional.empty());
        when(appelByReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(testOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CallResponseDto result = service.callPatient(REPORT_ID, USER_ID);

        assertThat(result.audioUrl()).contains("ANGLAIS");
    }

    @Test
    @DisplayName("callPatient - langue null → URL audio FRANCAIS (RÈGLE R6 fallback)")
    void callPatient_defaultLanguage_selectsFrancaisAudioUrl() {
        Report report = buildReport(null, "97000001");
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        mockSettings();
        when(ourVoiceClient.call(any(), any(), any(), any())).thenReturn("appel-789");
        when(appelByReportRepository.findByReportId(REPORT_ID)).thenReturn(Optional.empty());
        when(appelByReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(testOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CallResponseDto result = service.callPatient(REPORT_ID, USER_ID);

        assertThat(result.audioUrl()).contains("FRANCAIS");
    }

    @Test
    @DisplayName("callPatient - téléphone préfixé avec 229 envoyé à OurVoice (RÈGLE R7)")
    void callPatient_phoneNumberPrefixedWith229() {
        Report report = buildReport("français", "97000001");
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        mockSettings();
        when(ourVoiceClient.call(any(), any(), any(), any())).thenReturn("appel-123");
        when(appelByReportRepository.findByReportId(REPORT_ID)).thenReturn(Optional.empty());
        when(appelByReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(testOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.callPatient(REPORT_ID, USER_ID);

        verify(ourVoiceClient).call(any(), any(), eq("22997000001"), any());
    }

    @Test
    @DisplayName("callPatient - stocke appelId dans AppelByReport")
    void callPatient_storesAppelIdInAppelByReport() {
        Report report = buildReport("fr", "97000001");
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        mockSettings();
        when(ourVoiceClient.call(any(), any(), any(), any())).thenReturn("APPEL-ID-XYZ");
        when(appelByReportRepository.findByReportId(REPORT_ID)).thenReturn(Optional.empty());
        when(appelByReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(testOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.callPatient(REPORT_ID, USER_ID);

        ArgumentCaptor<AppelByReport> captor = ArgumentCaptor.forClass(AppelByReport.class);
        verify(appelByReportRepository).save(captor.capture());
        assertThat(captor.getValue().getAppelId()).isEqualTo("APPEL-ID-XYZ");
    }

    @Test
    @DisplayName("callPatient - met à jour testOrder.statusAppel")
    void callPatient_updatesTestOrderStatusAppel() {
        Report report = buildReport("fr", "97000001");
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        mockSettings();
        when(ourVoiceClient.call(any(), any(), any(), any())).thenReturn("APPEL-999");
        when(appelByReportRepository.findByReportId(REPORT_ID)).thenReturn(Optional.empty());
        when(appelByReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(testOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.callPatient(REPORT_ID, USER_ID);

        assertThat(report.getTestOrder().getStatusAppel()).isEqualTo("APPEL-999");
        verify(testOrderRepository).save(report.getTestOrder());
    }

    @Test
    @DisplayName("sendSms - téléphone préfixé avec 229 confié à la passerelle (RÈGLE R7)")
    void sendSms_prefixesPhoneWith229() {
        Report report = buildReport("fr", "97000002");
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        mockSettings();

        SmsResponseDto result = service.sendSms(REPORT_ID, USER_ID);

        verify(smsSender).envoyer(eq("22997000002"), any(), eq(SmsSender.SOURCE_RESULTAT), any());
        assertThat(result.status()).isEqualTo("sent");
    }

    @Test
    @DisplayName("sendSms - le SMS part par la passerelle SMS, jamais par l'appel vocal")
    void sendSms_neDeclencheAucunAppelVocal() {
        Report report = buildReport("fr", "97000003");
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        mockSettings();

        service.sendSms(REPORT_ID, USER_ID);

        verify(smsSender).envoyer(any(), any(), any(), any());
        // OurVoice ne sert plus qu'à la voix : un SMS ne doit pas passer par lui.
        verify(ourVoiceClient, org.mockito.Mockito.never()).call(any(), any(), any(), any());
    }

    @Test
    @DisplayName("sendSms - clé d'idempotence tirée au sort, pour que la relance passe")
    void sendSms_cleDIdempotenceVariableEntreDeuxEnvois() {
        // Le renvoi manuel d'un avis est un besoin de l'écran de suivi : une clé
        // stable ferait écarter le second envoi comme un doublon.
        Report report = buildReport("fr", "97000004");
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        mockSettings();

        service.sendSms(REPORT_ID, USER_ID);
        service.sendSms(REPORT_ID, USER_ID);

        ArgumentCaptor<UUID> cles = ArgumentCaptor.forClass(UUID.class);
        verify(smsSender, org.mockito.Mockito.times(2))
                .envoyer(any(), any(), eq(SmsSender.SOURCE_RESULTAT), cles.capture());
        assertThat(cles.getAllValues().get(0)).isNotEqualTo(cles.getAllValues().get(1));
    }

    @Test
    @DisplayName("callPatient - rapport inexistant → ResourceNotFoundException")
    void callPatient_unknownReport_throws() {
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.callPatient(REPORT_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

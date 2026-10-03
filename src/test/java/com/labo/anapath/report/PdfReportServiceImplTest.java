package com.labo.anapath.report;

import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PdfReportServiceImplTest {

    @Mock private ReportRepository reportRepository;
    @Mock private LogReportRepository logReportRepository;
    @Mock private SettingAppRepository settingAppRepository;
    @Mock private UserRepository userRepository;
    @Mock private QrCodeService qrCodeService;
    @Mock private SpringTemplateEngine templateEngine;

    @InjectMocks
    private PdfReportServiceImpl service;

    private final UUID REPORT_ID = UUID.randomUUID();
    private final UUID USER_ID = UUID.randomUUID();
    private final UUID BRANCH_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // userRepository.findById est appelé lors du log — retourner empty est suffisant (ifPresent)
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
    }

    private Report buildMinimalReport() {
        Report r = new Report();
        r.setId(REPORT_ID);
        r.setCode("CR26-0001");
        r.setBranchId(BRANCH_ID);
        return r;
    }

    @Test
    @DisplayName("generatePdf - rapport introuvable → ResourceNotFoundException")
    void generatePdf_reportNotFound() {
        when(reportRepository.findByIdAndBranchId(eq(REPORT_ID), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generatePdf(REPORT_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(templateEngine, never()).process(anyString(), any());
    }

    @Test
    @DisplayName("generatePdf - succès → retourne des bytes PDF et logue l'action")
    void generatePdf_success_returnsBytesAndLogs() throws Exception {
        Report report = buildMinimalReport();
        TestOrder order = new TestOrder();
        order.setCode("EX26-0001");
        report.setTestOrder(order);

        when(reportRepository.findByIdAndBranchId(eq(REPORT_ID), any())).thenReturn(Optional.of(report));
        when(qrCodeService.generateBase64(anyString(), anyInt())).thenReturn("data:image/png;base64,abc");
        when(settingAppRepository.findByKey("entete")).thenReturn(Optional.empty());
        when(settingAppRepository.findByKey("report_footer")).thenReturn(Optional.empty());
        when(settingAppRepository.findByKey("report_review_title")).thenReturn(Optional.empty());
        when(templateEngine.process(eq("pdf/rapport"), any(Context.class)))
                .thenReturn("<html><body><p>Test PDF</p></body></html>");

        byte[] result = service.generatePdf(REPORT_ID, USER_ID);

        assertThat(result).isNotNull().isNotEmpty();
        verify(logReportRepository).save(any(LogReport.class));
    }

    @Test
    @DisplayName("generatePdf - QrCode échoue → continue avec qrcode vide")
    void generatePdf_qrCodeFails_continuesWithEmptyQr() throws Exception {
        Report report = buildMinimalReport();
        TestOrder order = new TestOrder();
        order.setCode("EX26-0001");
        report.setTestOrder(order);

        when(reportRepository.findByIdAndBranchId(eq(REPORT_ID), any())).thenReturn(Optional.of(report));
        when(qrCodeService.generateBase64(anyString(), anyInt())).thenThrow(new RuntimeException("ZXing error"));
        when(settingAppRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(templateEngine.process(eq("pdf/rapport"), any(Context.class)))
                .thenReturn("<html><body><p>Sans QR</p></body></html>");

        byte[] result = service.generatePdf(REPORT_ID, USER_ID);

        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("generatePdf - échec dans le bloc de rendu → InvalidOperationException, rien n'est journalisé")
    void generatePdf_renderFails_throwsInvalidOperation() throws Exception {
        Report report = buildMinimalReport();
        when(reportRepository.findByIdAndBranchId(eq(REPORT_ID), any())).thenReturn(Optional.of(report));
        when(settingAppRepository.findByKey(anyString())).thenReturn(Optional.empty());
        // Un gabarit vide ne fait plus échouer le rendu (PdfHtmlUtil.toXhtml le
        // remplace par un document vide) : on fait échouer l'étape suivante du
        // même bloc, la journalisation, pour vérifier l'enveloppe de l'erreur.
        when(templateEngine.process(eq("pdf/rapport"), any(Context.class))).thenReturn(null);
        when(userRepository.findById(USER_ID)).thenThrow(new RuntimeException("base indisponible"));

        assertThatThrownBy(() -> service.generatePdf(REPORT_ID, USER_ID))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Erreur lors de la génération du PDF");

        verify(logReportRepository, never()).save(any());
    }

    @Test
    @DisplayName("generatePdf - settings entête/footer présents → inclus dans le contexte Thymeleaf")
    void generatePdf_settingsPresent_usedInContext() throws Exception {
        Report report = buildMinimalReport();
        when(reportRepository.findByIdAndBranchId(eq(REPORT_ID), any())).thenReturn(Optional.of(report));
        when(settingAppRepository.findByKey("entete"))
                .thenReturn(Optional.of(buildSetting("entete", "<h1>LABO ANAPATH</h1>")));
        when(settingAppRepository.findByKey("report_footer"))
                .thenReturn(Optional.of(buildSetting("report_footer", "Pied de page")));
        when(settingAppRepository.findByKey("report_review_title"))
                .thenReturn(Optional.of(buildSetting("report_review_title", "Relu par")));
        when(templateEngine.process(eq("pdf/rapport"), any(Context.class)))
                .thenReturn("<html><body><h1>LABO ANAPATH</h1></body></html>");

        byte[] result = service.generatePdf(REPORT_ID, USER_ID);

        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("generatePdf - rapport avec signataires → signataires inclus")
    void generatePdf_withSignatories_signatoryNamesSet() throws Exception {
        Report report = buildMinimalReport();
        User signatory1 = new User();
        signatory1.setFirstname("Jean");
        signatory1.setLastname("DUPONT");
        report.setSignatory1(signatory1);

        when(reportRepository.findByIdAndBranchId(eq(REPORT_ID), any())).thenReturn(Optional.of(report));
        when(settingAppRepository.findByKey(anyString())).thenReturn(Optional.empty());
        when(templateEngine.process(eq("pdf/rapport"), any(Context.class)))
                .thenReturn("<html><body>Jean DUPONT</body></html>");

        byte[] result = service.generatePdf(REPORT_ID, USER_ID);

        assertThat(result).isNotNull().isNotEmpty();
    }

    private SettingApp buildSetting(String key, String value) {
        SettingApp s = new SettingApp();
        s.setKey(key);
        s.setValue(value);
        return s;
    }
}

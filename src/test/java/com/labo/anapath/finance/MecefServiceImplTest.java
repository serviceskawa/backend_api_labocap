package com.labo.anapath.finance;

import com.labo.anapath.common.exception.ExternalApiException;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.setting.SettingInvoice;
import com.labo.anapath.setting.SettingInvoiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MecefServiceImplTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private SettingInvoiceRepository settingInvoiceRepository;
    @Mock private FinanceMapper financeMapper;
    @Mock private RestTemplate restTemplate;
    /** L'encaissement publie InvoiceValidatedEvent : sans ce mock, @InjectMocks
     *  laisse le champ à null et markAsPaid lève un NullPointerException. */
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private MecefServiceImpl service;

    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID  = UUID.randomUUID();

    private SettingInvoice buildSettingEnabled() {
        SettingInvoice s = new SettingInvoice();
        s.setToken("test-token");
        s.setIfu("IFU123");
        s.setStatus(true);
        return s;
    }

    private Invoice buildInvoice() {
        Invoice inv = new Invoice();
        inv.setBranchId(BRANCH_ID);
        inv.setTotal(new BigDecimal("5000.00"));
        inv.setPaid(false);
        inv.setStatus(InvoiceStatus.PENDING);
        return inv;
    }

    @Test
    @DisplayName("confirmInvoice - MECeF désactivé (pas de setting) → InvalidOperationException MECEF_DISABLED")
    void confirmMecef_noSetting_throwsMecefDisabled() {
        when(settingInvoiceRepository.findFirstByBranchId(BRANCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmInvoice(INVOICE_ID, "uid123", BRANCH_ID))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("MECEF_DISABLED");

        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(MecefApiResponse.class));
    }

    @Test
    @DisplayName("confirmInvoice - status=false → InvalidOperationException MECEF_DISABLED")
    void confirmMecef_statusFalse_throwsMecefDisabled() {
        SettingInvoice disabled = new SettingInvoice();
        disabled.setStatus(false);
        when(settingInvoiceRepository.findFirstByBranchId(BRANCH_ID)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> service.confirmInvoice(INVOICE_ID, "uid123", BRANCH_ID))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("MECEF_DISABLED");
    }

    @Test
    @DisplayName("confirmInvoice - API indisponible → ExternalApiException")
    void confirmMecef_apiDown_throwsExternalApiException() {
        when(settingInvoiceRepository.findFirstByBranchId(BRANCH_ID)).thenReturn(Optional.of(buildSettingEnabled()));
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(buildInvoice()));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(MecefApiResponse.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> service.confirmInvoice(INVOICE_ID, "uid123", BRANCH_ID))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("confirmInvoice - succès → champs MECeF mis à jour sur la facture")
    void confirmMecef_success_updatesInvoiceFields() {
        SettingInvoice setting = buildSettingEnabled();
        Invoice inv = buildInvoice();

        MecefApiResponse mecefResp = new MecefApiResponse();
        mecefResp.setCodeMECeFDGI("CODE123");
        mecefResp.setCounters("{\"a\":1}");
        mecefResp.setDateTime("2026-05-18T10:00:00");
        mecefResp.setNim("NIM456");
        mecefResp.setQrCode("base64data");

        when(settingInvoiceRepository.findFirstByBranchId(BRANCH_ID)).thenReturn(Optional.of(setting));
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(inv));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(MecefApiResponse.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.OK).body(mecefResp));
        when(invoiceRepository.save(inv)).thenReturn(inv);
        when(financeMapper.toInvoiceResponseDto(inv)).thenReturn(null);

        service.confirmInvoice(INVOICE_ID, "uid123", BRANCH_ID);

        assertThat(inv.getCodeMecef()).isEqualTo("CODE123");
        assertThat(inv.getNim()).isEqualTo("NIM456");
        assertThat(inv.getQrcode()).isEqualTo("base64data");
        assertThat(inv.getPaid()).isTrue();
    }

    @Test
    @DisplayName("cancelInvoice - API indisponible → ExternalApiException")
    void cancelMecef_apiDown_throwsExternalApiException() {
        when(settingInvoiceRepository.findFirstByBranchId(BRANCH_ID)).thenReturn(Optional.of(buildSettingEnabled()));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.PUT), any(), eq(Void.class)))
                .thenThrow(new ResourceAccessException("Timeout"));

        assertThatThrownBy(() -> service.cancelInvoice(INVOICE_ID, "uid123", BRANCH_ID))
                .isInstanceOf(ExternalApiException.class);
    }
}

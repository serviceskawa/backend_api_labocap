package com.labo.anapath.finance;

import com.labo.anapath.common.exception.ExternalApiException;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.setting.Setting;
import com.labo.anapath.setting.SettingRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MobileMoneyServiceImplTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private SettingRepository settingRepository;
    @Mock private RestTemplate restTemplate;

    @InjectMocks private MobileMoneyServiceImpl service;

    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID  = UUID.randomUUID();

    private Invoice buildInvoice() {
        Invoice inv = new Invoice();
        inv.setBranchId(BRANCH_ID);
        inv.setTotal(new BigDecimal("5000.00"));
        return inv;
    }

    private Setting buildTokenSetting() {
        Setting s = new Setting();
        s.setValue("Bearer sckaler-token-123");
        return s;
    }

    private MobileMoneyInitiateRequestDto buildDto() {
        MobileMoneyInitiateRequestDto dto = new MobileMoneyInitiateRequestDto();
        dto.setInvoiceId(INVOICE_ID);
        dto.setPhone("97123456");
        dto.setAmount("5000");
        dto.setProvider("MOBILEMONEY-MTN");
        dto.setFee("100");
        return dto;
    }

    @Test
    @DisplayName("initiate - paiement SUCCESS existant → InvalidOperationException PAYMENT_ALREADY_SUCCESS")
    void initiatePayment_alreadySuccess_throws422() {
        Payment existing = new Payment();
        existing.setPaymentStatus("SUCCESS");

        when(paymentRepository.findByInvoiceId(INVOICE_ID)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.initiate(buildDto(), BRANCH_ID))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("PAYMENT_ALREADY_SUCCESS");

        verify(restTemplate, never()).exchange(anyString(), any(), any(), eq(MobileMoneyServiceImpl.SckalerCollectionResponse.class));
    }

    @Test
    @DisplayName("initiate - Sckaler indisponible → ExternalApiException")
    void initiatePayment_skkalerDown_throwsExternalApiException() {
        when(paymentRepository.findByInvoiceId(INVOICE_ID)).thenReturn(Optional.empty());
        when(invoiceRepository.findByIdAndBranchId(eq(INVOICE_ID), any())).thenReturn(Optional.of(buildInvoice()));
        when(settingRepository.findByKeyAndBranchId("token_payment", BRANCH_ID)).thenReturn(Optional.of(buildTokenSetting()));
        when(restTemplate.exchange(contains("mtn"), eq(HttpMethod.POST), any(), eq(MobileMoneyServiceImpl.SckalerCollectionResponse.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        assertThatThrownBy(() -> service.initiate(buildDto(), BRANCH_ID))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("initiate - R7 : le numéro envoyé à Sckaler commence par '229'")
    void initiatePayment_mtn_addsPrefix229() {
        MobileMoneyServiceImpl.SckalerCollectionResponse sckalerResp = new MobileMoneyServiceImpl.SckalerCollectionResponse();
        sckalerResp.setStatus("SUCCESS");
        sckalerResp.setTransactionId("txn-abc");

        when(paymentRepository.findByInvoiceId(INVOICE_ID)).thenReturn(Optional.empty());
        when(invoiceRepository.findByIdAndBranchId(eq(INVOICE_ID), any())).thenReturn(Optional.of(buildInvoice()));
        when(settingRepository.findByKeyAndBranchId("token_payment", BRANCH_ID)).thenReturn(Optional.of(buildTokenSetting()));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(MobileMoneyServiceImpl.SckalerCollectionResponse.class)))
                .thenReturn(ResponseEntity.ok(sckalerResp));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.initiate(buildDto(), BRANCH_ID);

        ArgumentCaptor<org.springframework.http.HttpEntity<?>> entityCaptor =
                ArgumentCaptor.forClass(org.springframework.http.HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(),
                eq(MobileMoneyServiceImpl.SckalerCollectionResponse.class));

        MobileMoneyServiceImpl.SckalerRequest body =
                (MobileMoneyServiceImpl.SckalerRequest) entityCaptor.getValue().getBody();
        assertThat(body.getTel()).startsWith("229");
        assertThat(body.getTel()).isEqualTo("22997123456");
    }

    @Test
    @DisplayName("initiate - succès Sckaler → payment créé avec paymentStatus=INITIATED")
    void initiatePayment_success_createsPaymentInitiated() {
        MobileMoneyServiceImpl.SckalerCollectionResponse sckalerResp = new MobileMoneyServiceImpl.SckalerCollectionResponse();
        sckalerResp.setStatus("SUCCESS");
        sckalerResp.setTransactionId("txn-xyz");

        when(paymentRepository.findByInvoiceId(INVOICE_ID)).thenReturn(Optional.empty());
        when(invoiceRepository.findByIdAndBranchId(eq(INVOICE_ID), any())).thenReturn(Optional.of(buildInvoice()));
        when(settingRepository.findByKeyAndBranchId("token_payment", BRANCH_ID)).thenReturn(Optional.of(buildTokenSetting()));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(MobileMoneyServiceImpl.SckalerCollectionResponse.class)))
                .thenReturn(ResponseEntity.ok(sckalerResp));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MobileMoneyStatusResponseDto result = service.initiate(buildDto(), BRANCH_ID);

        assertThat(result.message()).isEqualTo("INITIATED");
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getPaymentStatus()).isEqualTo("INITIATED");
        assertThat(paymentCaptor.getValue().getPaymentId()).isEqualTo("txn-xyz");
        assertThat(paymentCaptor.getValue().getPaymentNumber()).isEqualTo("22997123456"); // normalisé avec préfixe pays
    }

    @Test
    @DisplayName("initiate - paiement PENDING existant → réutilise l'enregistrement")
    void initiatePayment_existingPending_reusesRecord() {
        Payment existing = new Payment();
        existing.setPaymentStatus("FAILED");
        existing.setInvoice(buildInvoice());

        MobileMoneyServiceImpl.SckalerCollectionResponse sckalerResp = new MobileMoneyServiceImpl.SckalerCollectionResponse();
        sckalerResp.setStatus("SUCCESS");
        sckalerResp.setTransactionId("txn-new");

        when(paymentRepository.findByInvoiceId(INVOICE_ID)).thenReturn(Optional.of(existing));
        when(invoiceRepository.findByIdAndBranchId(eq(INVOICE_ID), any())).thenReturn(Optional.of(buildInvoice()));
        when(settingRepository.findByKeyAndBranchId("token_payment", BRANCH_ID)).thenReturn(Optional.of(buildTokenSetting()));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(), eq(MobileMoneyServiceImpl.SckalerCollectionResponse.class)))
                .thenReturn(ResponseEntity.ok(sckalerResp));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.initiate(buildDto(), BRANCH_ID);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        // Vérifie que c'est le même objet "existing" réutilisé (pas un new Payment())
        assertThat(captor.getValue()).isSameAs(existing);
    }

    @Test
    @DisplayName("checkStatus - Sckaler indisponible → ExternalApiException")
    void checkStatus_skkalerDown_throwsExternalApiException() {
        Payment payment = new Payment();
        payment.setPaymentId("txn-123");

        when(paymentRepository.findByIdAndBranchId(eq(PAYMENT_ID), any())).thenReturn(Optional.of(payment));
        when(settingRepository.findByKeyAndBranchId("token_payment", BRANCH_ID)).thenReturn(Optional.of(buildTokenSetting()));
        when(restTemplate.exchange(contains("txn-123"), eq(HttpMethod.GET), any(),
                eq(MobileMoneyServiceImpl.SckalerStatusResponse.class)))
                .thenThrow(new ResourceAccessException("Timeout"));

        assertThatThrownBy(() -> service.checkStatus(PAYMENT_ID, BRANCH_ID))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("checkStatus - succès → paymentStatus mis à jour depuis Sckaler")
    void checkStatus_updatesPaymentStatus() {
        Payment payment = new Payment();
        payment.setPaymentId("txn-123");

        MobileMoneyServiceImpl.SckalerStatusResponse statusResp = new MobileMoneyServiceImpl.SckalerStatusResponse();
        statusResp.setStatus("SUCCESS");

        when(paymentRepository.findByIdAndBranchId(eq(PAYMENT_ID), any())).thenReturn(Optional.of(payment));
        when(settingRepository.findByKeyAndBranchId("token_payment", BRANCH_ID)).thenReturn(Optional.of(buildTokenSetting()));
        when(restTemplate.exchange(contains("txn-123"), eq(HttpMethod.GET), any(),
                eq(MobileMoneyServiceImpl.SckalerStatusResponse.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.OK).body(statusResp));
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        MobileMoneyStatusResponseDto result = service.checkStatus(PAYMENT_ID, BRANCH_ID);

        assertThat(result.message()).isEqualTo("SUCCESS");
        assertThat(payment.getPaymentStatus()).isEqualTo("SUCCESS");
    }
}

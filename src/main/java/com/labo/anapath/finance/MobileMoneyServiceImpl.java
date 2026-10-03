package com.labo.anapath.finance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.labo.anapath.common.exception.ExternalApiException;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.setting.Setting;
import com.labo.anapath.setting.SettingRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MobileMoneyServiceImpl implements MobileMoneyService {

    private static final String SCKALER_MTN_URL    = "https://pay.sckaler.cloud/api/collection/mtn";
    private static final String SCKALER_MOOV_URL   = "https://pay.sckaler.cloud/api/collection/moov";
    private static final String SCKALER_STATUS_URL = "https://pay.sckaler.cloud/api/transaction/status/";
    private static final String TOKEN_PAYMENT_KEY  = "token_payment";

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final SettingRepository settingRepository;
    private final RestTemplate restTemplate;

    @Override
    @Transactional
    public MobileMoneyStatusResponseDto initiate(MobileMoneyInitiateRequestDto dto, UUID branchId) {
        log.info("Initiation paiement Mobile Money — invoice={}, montant={}, provider={}",
                dto.getInvoiceId(), dto.getAmount(), dto.getProvider());

        // AC3 — anti-doublon SUCCESS
        paymentRepository.findByInvoiceId(dto.getInvoiceId()).ifPresent(existing -> {
            if ("SUCCESS".equals(existing.getPaymentStatus())) {
                throw new InvalidOperationException("PAYMENT_ALREADY_SUCCESS");
            }
        });

        Invoice invoice = invoiceRepository.findByIdAndBranchId(dto.getInvoiceId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", dto.getInvoiceId()));

        String tokenValue = requireTokenPayment(branchId);

        String orderCode = invoice.getTestOrder() != null ? invoice.getTestOrder().getCode() : "N/A";
        String description = "CAAP : " + orderCode + ". Frais : " + dto.getFee();

        String normalizedPhone = dto.getPhone().startsWith("229") ? dto.getPhone() : "229" + dto.getPhone();
        SckalerRequest body = new SckalerRequest(normalizedPhone, dto.getAmount(), description);
        String url = "MOBILEMONEY-MTN".equals(dto.getProvider()) ? SCKALER_MTN_URL : SCKALER_MOOV_URL;

        SckalerCollectionResponse sckalerResponse;
        try {
            HttpEntity<SckalerRequest> entity = new HttpEntity<>(body, jsonHeaders(tokenValue));
            ResponseEntity<SckalerCollectionResponse> resp = restTemplate.exchange(
                    url, HttpMethod.POST, entity, SckalerCollectionResponse.class);
            sckalerResponse = resp.getBody();
        } catch (RestClientException e) {
            log.error("Sckaler API indisponible: {}", e.getMessage());
            throw new ExternalApiException("Sckaler indisponible", e);
        }

        if (sckalerResponse == null || !"SUCCESS".equals(sckalerResponse.getStatus())) {
            return new MobileMoneyStatusResponseDto("FAILED");
        }

        Payment payment = paymentRepository.findByInvoiceId(dto.getInvoiceId()).orElse(new Payment());
        payment.setBranchId(branchId);
        payment.setInvoice(invoice);
        payment.setPaymentName(dto.getProvider());
        payment.setPaymentNumber(normalizedPhone);
        payment.setPaymentStatus("INITIATED");
        payment.setPaymentAmount(dto.getAmount());
        payment.setPaymentId(sckalerResponse.getTransactionId());
        payment.setDescription(description);
        payment.setPaymentDate(LocalDate.now());
        payment.setAmount(java.math.BigDecimal.ZERO);
        paymentRepository.save(payment);
        log.info("Paiement Mobile Money initié — paymentId={}, statut=INITIATED", payment.getPaymentId());

        return new MobileMoneyStatusResponseDto("INITIATED");
    }

    @Override
    @Transactional
    public MobileMoneyStatusResponseDto checkStatus(UUID paymentId, UUID branchId) {
        Payment payment = paymentRepository.findByIdAndBranchId(paymentId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement", paymentId));

        String tokenValue = requireTokenPayment(branchId);

        try {
            HttpEntity<Void> entity = new HttpEntity<>(jsonHeaders(tokenValue));
            ResponseEntity<SckalerStatusResponse> resp = restTemplate.exchange(
                    SCKALER_STATUS_URL + payment.getPaymentId(),
                    HttpMethod.GET, entity, SckalerStatusResponse.class);

            String status = resp.getBody() != null ? resp.getBody().getStatus() : "FAILED";
            payment.setPaymentStatus(status);
            paymentRepository.save(payment);
            return new MobileMoneyStatusResponseDto(status);
        } catch (RestClientException e) {
            log.error("Sckaler status API indisponible: {}", e.getMessage());
            throw new ExternalApiException("Sckaler indisponible", e);
        }
    }

    private String requireTokenPayment(UUID branchId) {
        Setting setting = settingRepository.findByKeyAndBranchId(TOKEN_PAYMENT_KEY, branchId)
                .orElseThrow(() -> new InvalidOperationException("TOKEN_PAYMENT_NOT_CONFIGURED"));
        return setting.getValue();
    }

    private HttpHeaders jsonHeaders(String token) {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", token);
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        return h;
    }

    // Internal DTOs for Sckaler API

    @Getter
    @Setter
    static class SckalerRequest {
        private final String tel;
        private final String amount;
        private final String description;

        SckalerRequest(String tel, String amount, String description) {
            this.tel = tel;
            this.amount = amount;
            this.description = description;
        }
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SckalerCollectionResponse {
        private String msg;
        private String status;
        @JsonProperty("transaction_id")
        private String transactionId;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SckalerStatusResponse {
        private String status;
    }
}

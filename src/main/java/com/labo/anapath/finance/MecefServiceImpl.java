package com.labo.anapath.finance;

import com.labo.anapath.common.exception.ExternalApiException;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.setting.SettingInvoice;
import com.labo.anapath.setting.SettingInvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MecefServiceImpl implements MecefService {

    private static final String MECEF_BASE_URL = "https://developper.impots.bj/sygmef-emcf/api/invoice";

    private final InvoiceRepository invoiceRepository;
    private final SettingInvoiceRepository settingInvoiceRepository;
    private final FinanceMapper financeMapper;
    private final RestTemplate restTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public InvoiceResponseDto confirmInvoice(UUID invoiceId, String uid, UUID branchId) {
        SettingInvoice setting = requireMecefEnabled(branchId);

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", invoiceId));

        log.info("Appel MECeF confirm — invoiceId={}, uid={}", invoiceId, uid);
        MecefApiResponse mecefResponse;
        try {
            mecefResponse = callMecef(uid + "/confirm", setting.getToken(), HttpMethod.PUT, MecefApiResponse.class);
        } catch (RestClientException e) {
            log.error("MECeF confirm API indisponible: {}", e.getMessage());
            throw new ExternalApiException("MECeF indisponible", e);
        }

        invoice.setPaid(true);
        invoice.setStatus(InvoiceStatus.PAID);
        if (mecefResponse != null) {
            invoice.setCodeMecef(mecefResponse.getCodeMECeFDGI());
            invoice.setCounters(mecefResponse.getCounters());
            invoice.setDateGenerate(mecefResponse.getDateTime());
            invoice.setNim(mecefResponse.getNim());
            invoice.setQrcode(mecefResponse.getQrCode());
        }
        log.info("MECeF confirm réussi — invoiceId={}, codeMecef={}", invoiceId,
                mecefResponse != null ? mecefResponse.getCodeMECeFDGI() : "N/A");

        Invoice normalisee = invoiceRepository.save(invoice);

        // La confirmation MECeF vaut validation : la facture est due et son
        // document existe. Le client en reçoit le lien par SMS — sans doublon si
        // elle avait déjà été encaissée, voir InvoiceValidatedEvent.
        eventPublisher.publishEvent(new InvoiceValidatedEvent(normalisee.getId()));

        return financeMapper.toInvoiceResponseDto(normalisee);
    }

    @Override
    @Transactional
    public void cancelInvoice(UUID invoiceId, String uid, UUID branchId) {
        SettingInvoice setting = requireMecefEnabled(branchId);

        log.info("Appel MECeF cancel — invoiceId={}, uid={}", invoiceId, uid);
        try {
            callMecef(uid + "/cancel", setting.getToken(), HttpMethod.PUT, Void.class);
        } catch (RestClientException e) {
            log.error("MECeF cancel API indisponible: {}", e.getMessage());
            throw new ExternalApiException("MECeF indisponible", e);
        }
        log.info("MECeF cancel réussi — invoiceId={}", invoiceId);
    }

    private SettingInvoice requireMecefEnabled(UUID branchId) {
        SettingInvoice setting = settingInvoiceRepository.findFirstByBranchId(branchId)
                .orElseThrow(() -> new InvalidOperationException("MECEF_DISABLED"));
        if (!Boolean.TRUE.equals(setting.getStatus())) {
            throw new InvalidOperationException("MECEF_DISABLED");
        }
        return setting;
    }

    private <T> T callMecef(String path, String token, HttpMethod method, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<T> response = restTemplate.exchange(
                MECEF_BASE_URL + "/" + path,
                method,
                new HttpEntity<>(headers),
                responseType);
        return response.getBody();
    }
}

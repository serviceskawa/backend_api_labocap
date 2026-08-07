package com.labo.anapath.finance;

import com.labo.anapath.common.exception.InvalidOperationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock private RefundRequestRepository refundRequestRepository;
    @Mock private RefundRequestLogRepository refundRequestLogRepository;
    @Mock private InvoiceRepository invoiceRepository;
    // Lu à la création du journal de remboursement.
    @Mock private RefundReasonRepository refundReasonRepository;

    @InjectMocks private RefundServiceImpl service;

    private static final UUID INVOICE_ID   = UUID.randomUUID();
    private static final UUID REFUND_ID    = UUID.randomUUID();
    private static final UUID BRANCH_ID    = UUID.randomUUID();
    private static final UUID USER_ID      = UUID.randomUUID();
    private static final UUID REASON_ID    = UUID.randomUUID();

    private Invoice buildInvoice() {
        Invoice inv = new Invoice();
        inv.setBranchId(BRANCH_ID);
        inv.setTotal(new BigDecimal("5000.00"));
        inv.setPaid(true);
        inv.setStatusInvoice(0);
        return inv;
    }

    private RefundRequestCreateDto buildDto() {
        RefundRequestCreateDto dto = new RefundRequestCreateDto();
        dto.setInvoiceId(INVOICE_ID);
        dto.setRefundReasonId(REASON_ID);
        dto.setMontant(new BigDecimal("3000.00"));
        dto.setNote("Test remboursement");
        return dto;
    }

    @Test
    @DisplayName("create - doublon invoiceId → InvalidOperationException REFUND_ALREADY_EXISTS")
    void createRefund_duplicateInvoice_throws422() {
        when(refundRequestRepository.existsByInvoiceId(INVOICE_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.create(buildDto(), BRANCH_ID, USER_ID))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("REFUND_ALREADY_EXISTS");

        verify(invoiceRepository, never()).findById(any());
    }

    @Test
    @DisplayName("create - montant > invoice.total → InvalidOperationException REFUND_AMOUNT_EXCEEDS_INVOICE")
    void createRefund_montantExceedsInvoice_throws422() {
        when(refundRequestRepository.existsByInvoiceId(INVOICE_ID)).thenReturn(false);
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(buildInvoice()));

        RefundRequestCreateDto dto = buildDto();
        dto.setMontant(new BigDecimal("9999.00")); // > 5000

        assertThatThrownBy(() -> service.create(dto, BRANCH_ID, USER_ID))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("REFUND_AMOUNT_EXCEEDS_INVOICE");
    }

    @Test
    @DisplayName("create - succès → log initial 'En attente' créé")
    void createRefund_createsInitialLog() {
        when(refundRequestRepository.existsByInvoiceId(INVOICE_ID)).thenReturn(false);
        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(buildInvoice()));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refundRequestLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refundRequestLogRepository.findByRefundRequestId(any())).thenReturn(Collections.emptyList());

        service.create(buildDto(), BRANCH_ID, USER_ID);

        ArgumentCaptor<RefundRequestLog> logCaptor = ArgumentCaptor.forClass(RefundRequestLog.class);
        verify(refundRequestLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getOperation()).isEqualTo("En attente");
        assertThat(logCaptor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    @Test
    @DisplayName("updateStatus - 'Aprouvé' (R1) → facture avoir créée avec statusInvoice=1")
    void updateStatus_Aprouve_createsAvoirInvoice() {
        RefundRequest refund = new RefundRequest();
        refund.setBranchId(BRANCH_ID);
        refund.setInvoice(buildInvoice());
        refund.setMontant(new BigDecimal("3000.00"));

        when(refundRequestRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refundRequestLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.findByBranchIdAndCodeNotNullAndYear(any(), anyInt(), any()))
                .thenReturn(Collections.emptyList());
        Invoice savedAvoir = new Invoice();
        savedAvoir.setBranchId(BRANCH_ID);
        savedAvoir.setPaid(false);
        when(invoiceRepository.save(any())).thenReturn(savedAvoir);

        RefundRequestStatusUpdateDto dto = new RefundRequestStatusUpdateDto();
        dto.setStatus("Aprouvé"); // R1 : UN SEUL 'p'

        RefundRequestStatusResult result = service.updateStatus(REFUND_ID, dto, USER_ID);

        assertThat(result.status()).isEqualTo("Aprouvé");
        ArgumentCaptor<Invoice> avoirCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(avoirCaptor.capture());
        assertThat(avoirCaptor.getValue().getStatusInvoice()).isEqualTo(1);
        assertThat(avoirCaptor.getValue().getTotal()).isEqualByComparingTo(new BigDecimal("3000.00"));
    }

    @Test
    @DisplayName("updateStatus - 'Aprouvé' → log avec operation='Aprouvé' créé (R1)")
    void updateStatus_Aprouve_createsLog() {
        RefundRequest refund = new RefundRequest();
        refund.setBranchId(BRANCH_ID);
        refund.setInvoice(buildInvoice());
        refund.setMontant(new BigDecimal("2000.00"));

        when(refundRequestRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refundRequestLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.findByBranchIdAndCodeNotNullAndYear(any(), anyInt(), any()))
                .thenReturn(Collections.emptyList());
        Invoice savedAvoir = new Invoice();
        savedAvoir.setPaid(false);
        when(invoiceRepository.save(any())).thenReturn(savedAvoir);

        RefundRequestStatusUpdateDto dto = new RefundRequestStatusUpdateDto();
        dto.setStatus("Aprouvé");
        service.updateStatus(REFUND_ID, dto, USER_ID);

        ArgumentCaptor<RefundRequestLog> logCaptor = ArgumentCaptor.forClass(RefundRequestLog.class);
        verify(refundRequestLogRepository, times(1)).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getOperation()).isEqualTo("Aprouvé");
    }

    @Test
    @DisplayName("updateStatus - 'Rejeté' → passe en 'Clôturé' + log")
    void updateStatus_Rejete_closesRequest() {
        RefundRequest refund = new RefundRequest();
        refund.setBranchId(BRANCH_ID);
        refund.setStatus("En attente");

        when(refundRequestRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refundRequestLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        RefundRequestStatusUpdateDto dto = new RefundRequestStatusUpdateDto();
        dto.setStatus("Rejeté");

        RefundRequestStatusResult result = service.updateStatus(REFUND_ID, dto, USER_ID);

        assertThat(result.status()).isEqualTo("Clôturé");
        assertThat(refund.getStatus()).isEqualTo("Clôturé");
        // 2 logs : "Rejeté" puis "Clôturé"
        verify(refundRequestLogRepository, times(2)).save(any(RefundRequestLog.class));
    }

    @Test
    @DisplayName("updateStatus - 'Aprouvé' avec pièce jointe + avoir payé → auto-clôture")
    void updateStatus_Aprouve_withAttachmentAndPaidAvoir_autoclotures() {
        RefundRequest refund = new RefundRequest();
        refund.setBranchId(BRANCH_ID);
        refund.setInvoice(buildInvoice());
        refund.setMontant(new BigDecimal("1000.00"));
        refund.setAttachment("/tickets/test.pdf");

        when(refundRequestRepository.findById(REFUND_ID)).thenReturn(Optional.of(refund));
        when(refundRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(refundRequestLogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(invoiceRepository.findByBranchIdAndCodeNotNullAndYear(any(), anyInt(), any()))
                .thenReturn(Collections.emptyList());
        Invoice savedAvoir = new Invoice();
        savedAvoir.setPaid(true); // avoir déjà payé → auto-clôture
        when(invoiceRepository.save(any())).thenReturn(savedAvoir);

        RefundRequestStatusUpdateDto dto = new RefundRequestStatusUpdateDto();
        dto.setStatus("Aprouvé");
        service.updateStatus(REFUND_ID, dto, USER_ID);

        assertThat(refund.getStatus()).isEqualTo("Clôturé");
        // Logs : "Aprouvé" + "Clôturé"
        verify(refundRequestLogRepository, times(2)).save(any(RefundRequestLog.class));
    }
}

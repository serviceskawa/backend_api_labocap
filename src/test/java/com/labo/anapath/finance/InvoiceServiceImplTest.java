package com.labo.anapath.finance;

import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.contract.Contrat;
import com.labo.anapath.contract.ContratRepository;
import com.labo.anapath.patient.PatientRepository;
import com.labo.anapath.test.LabTestRepository;
import com.labo.anapath.testorder.TestOrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceImplTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private TestOrderRepository testOrderRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private LabTestRepository labTestRepository;
    @Mock private CashboxRepository cashboxRepository;
    @Mock private ContratRepository contratRepository;
    @Mock private FinanceMapper financeMapper;

    @InjectMocks private InvoiceServiceImpl service;

    private static final UUID INVOICE_ID  = UUID.randomUUID();
    private static final UUID BRANCH_ID   = UUID.randomUUID();
    private static final UUID CONTRAT_ID  = UUID.randomUUID();

    private Invoice buildInvoice(int statusInvoice) {
        Invoice inv = new Invoice();
        inv.setBranchId(BRANCH_ID);
        inv.setTotal(new BigDecimal("5000.00"));
        inv.setPaid(false);
        inv.setStatusInvoice(statusInvoice);
        inv.setStatus(InvoiceStatus.PENDING);
        return inv;
    }

    @Test
    @DisplayName("markAsPaid - facture déjà payée → InvalidOperationException INVOICE_ALREADY_PAID")
    void markAsPaid_alreadyPaid_throws422() {
        Invoice inv = buildInvoice(0);
        inv.setPaid(true);

        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(inv));

        InvoiceStatusUpdateDto dto = new InvoiceStatusUpdateDto();
        dto.setPayment("ESPECES");

        assertThatThrownBy(() -> service.markAsPaid(INVOICE_ID, dto, BRANCH_ID))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("INVOICE_ALREADY_PAID");

        verify(cashboxRepository, never()).findFirstByBranchIdAndType(any(), any());
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("markAsPaid - vente (statusInvoice=0) → crédit caisse vente")
    void markAsPaid_vente_creditsCashboxVente() {
        Invoice inv = buildInvoice(0);
        Cashbox cashVente = new Cashbox();
        cashVente.setBranchId(BRANCH_ID);
        cashVente.setType("vente");
        cashVente.setBalance(new BigDecimal("10000.00"));

        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(inv));
        when(cashboxRepository.findFirstByBranchIdAndType(BRANCH_ID, "vente")).thenReturn(Optional.of(cashVente));
        when(cashboxRepository.save(cashVente)).thenReturn(cashVente);
        when(invoiceRepository.save(any())).thenReturn(inv);
        when(financeMapper.toInvoiceResponseDto(inv)).thenReturn(null);

        InvoiceStatusUpdateDto dto = new InvoiceStatusUpdateDto();
        dto.setPayment("ESPECES");

        service.markAsPaid(INVOICE_ID, dto, BRANCH_ID);

        ArgumentCaptor<Cashbox> captor = ArgumentCaptor.forClass(Cashbox.class);
        verify(cashboxRepository).save(captor.capture());
        assertThat(captor.getValue().getBalance()).isEqualByComparingTo(new BigDecimal("15000.00"));
        assertThat(inv.getPaid()).isTrue();
        assertThat(inv.getPayment()).isEqualTo("ESPECES");
    }

    @Test
    @DisplayName("markAsPaid - avoir (statusInvoice=1) → débit caisse dépense")
    void markAsPaid_avoir_debitsCashboxDepense() {
        Invoice inv = buildInvoice(1);
        Cashbox cashDepense = new Cashbox();
        cashDepense.setBranchId(BRANCH_ID);
        cashDepense.setType("depense");
        cashDepense.setBalance(new BigDecimal("3000.00"));

        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(inv));
        when(cashboxRepository.findFirstByBranchIdAndType(BRANCH_ID, "depense")).thenReturn(Optional.of(cashDepense));
        when(cashboxRepository.save(cashDepense)).thenReturn(cashDepense);
        when(invoiceRepository.save(any())).thenReturn(inv);
        when(financeMapper.toInvoiceResponseDto(inv)).thenReturn(null);

        InvoiceStatusUpdateDto dto = new InvoiceStatusUpdateDto();
        dto.setPayment("VIREMENT");

        service.markAsPaid(INVOICE_ID, dto, BRANCH_ID);

        ArgumentCaptor<Cashbox> captor = ArgumentCaptor.forClass(Cashbox.class);
        verify(cashboxRepository).save(captor.capture());
        assertThat(captor.getValue().getBalance()).isEqualByComparingTo(new BigDecimal("-2000.00"));
    }

    @Test
    @DisplayName("markAsPaid - contrat invoiceUnique=true → isClose = true")
    void markAsPaid_invoiceUnique_closesContract() {
        Contrat contrat = new Contrat();
        contrat.setInvoiceUnique(true);
        contrat.setIsClose(false);

        Invoice inv = buildInvoice(0);
        inv.setContrat(contrat);

        Cashbox cashVente = new Cashbox();
        cashVente.setBranchId(BRANCH_ID);
        cashVente.setType("vente");
        cashVente.setBalance(BigDecimal.ZERO);

        when(invoiceRepository.findById(INVOICE_ID)).thenReturn(Optional.of(inv));
        when(cashboxRepository.findFirstByBranchIdAndType(BRANCH_ID, "vente")).thenReturn(Optional.of(cashVente));
        when(cashboxRepository.save(any())).thenReturn(cashVente);
        when(invoiceRepository.save(any())).thenReturn(inv);
        when(financeMapper.toInvoiceResponseDto(inv)).thenReturn(null);

        InvoiceStatusUpdateDto dto = new InvoiceStatusUpdateDto();
        dto.setPayment("MOBILEMONEY");

        service.markAsPaid(INVOICE_ID, dto, BRANCH_ID);

        assertThat(contrat.getIsClose()).isTrue();
        verify(contratRepository).save(contrat);
    }

    @Test
    @DisplayName("getBusinessDashboard - retourne les 3 totaux corrects")
    void getBusinessDashboard_returnsThreeTotals() {
        LocalDate today = LocalDate.now();
        int currentMonth = today.getMonthValue();
        int currentYear = today.getYear();
        int lastMonth = today.minusMonths(1).getMonthValue();
        int lastYear = today.minusMonths(1).getYear();

        when(invoiceRepository.sumPaidByBranchIdAndDate(BRANCH_ID, today))
                .thenReturn(new BigDecimal("2000.00"));
        when(invoiceRepository.sumPaidByBranchIdAndMonth(BRANCH_ID, currentMonth, currentYear))
                .thenReturn(new BigDecimal("50000.00"));
        when(invoiceRepository.sumPaidByBranchIdAndMonth(BRANCH_ID, lastMonth, lastYear))
                .thenReturn(new BigDecimal("45000.00"));

        BusinessDashboardDto result = service.getBusinessDashboard(BRANCH_ID);

        assertThat(result.totalToday()).isEqualByComparingTo(new BigDecimal("2000.00"));
        assertThat(result.totalMonth()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(result.totalLastMonth()).isEqualByComparingTo(new BigDecimal("45000.00"));
    }
}

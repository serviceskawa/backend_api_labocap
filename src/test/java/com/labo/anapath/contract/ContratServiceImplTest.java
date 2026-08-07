package com.labo.anapath.contract;

import com.labo.anapath.client.ClientRepository;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.doctor.HospitalRepository;
import com.labo.anapath.finance.Invoice;
import com.labo.anapath.finance.InvoiceRepository;
import com.labo.anapath.test.CategoryTest;
import com.labo.anapath.test.LabTest;
import com.labo.anapath.test.LabTestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContratServiceImplTest {

    @Mock private ContratRepository contratRepository;
    @Mock private HospitalRepository hospitalRepository;
    @Mock private LabTestRepository labTestRepository;
    @Mock private DetailsContratRepository detailsContratRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private ClientRepository clientRepository;
    @Mock private ContratMapper contratMapper;

    @InjectMocks private ContratServiceImpl service;

    private final UUID BRANCH_ID = UUID.randomUUID();
    private final UUID CONTRACT_ID = UUID.randomUUID();
    private final UUID CATEGORY_TEST_ID = UUID.randomUUID();
    private final UUID LAB_TEST_ID = UUID.randomUUID();

    private Contrat buildContrat() {
        Contrat c = new Contrat();
        c.setBranchId(BRANCH_ID);
        c.setStatus("INACTIF");
        c.setInvoiceUnique(true);
        c.setDetails(new ArrayList<>());
        c.setStartDate(LocalDate.now());
        c.setNbrTests(10);
        return c;
    }

    @Test
    @DisplayName("activate - invoiceUnique=true sans facture existante → crée une Invoice groupée")
    void createContract_withInvoiceUnique_createsGroupedInvoice() {
        Contrat contrat = buildContrat();
        ContratResponseDto mockDto = new ContratResponseDto(
                CONTRACT_ID, null, null, null, null, null, null, null,
                10, LocalDate.now(), null, "ACTIF", true, false,
                // branchId, createdAt, updatedAt, usedTestsCount, invoice.
                new ArrayList<>(), BRANCH_ID, null, null, null, null);

        when(contratRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contrat));
        when(invoiceRepository.findFirstByContratIdOrderByCreatedAtDesc(CONTRACT_ID))
                .thenReturn(Optional.empty());
        when(invoiceRepository.findByBranchIdAndCodeNotNullAndYear(any(), any(), any()))
                .thenReturn(new ArrayList<>());
        when(invoiceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(contratRepository.save(any())).thenReturn(contrat);
        when(contratMapper.toResponseDto(contrat)).thenReturn(mockDto);

        service.activate(CONTRACT_ID);

        verify(invoiceRepository).save(any(Invoice.class));
        assertThat(contrat.getStatus()).isEqualTo("ACTIF");
    }

    @Test
    @DisplayName("addCategoryDetail - doublon même catégorie → InvalidOperationException")
    void addCategoryDetail_duplicate_throws422() {
        Contrat contrat = buildContrat();
        DetailsContrat existing = new DetailsContrat();
        existing.setCategoryTestId(CATEGORY_TEST_ID);
        existing.setPourcentage(new BigDecimal("10.00"));
        contrat.getDetails().add(existing);

        when(contratRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contrat));

        CategoryDetailRequestDto dto = new CategoryDetailRequestDto();
        dto.setCategoryTestId(CATEGORY_TEST_ID);
        dto.setDiscount(new BigDecimal("15.00"));

        assertThatThrownBy(() -> service.addCategoryDetail(CONTRACT_ID, dto))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("CATEGORY_DETAIL_ALREADY_EXISTS");
    }

    @Test
    @DisplayName("addTestDetail - categoryTestId récupérée depuis labTest")
    void addTestDetail_calculatesAmountAfterRemise() {
        Contrat contrat = buildContrat();

        CategoryTest category = new CategoryTest();
        category.setId(CATEGORY_TEST_ID);

        LabTest labTest = new LabTest();
        labTest.setId(LAB_TEST_ID);
        labTest.setName("Test ABC");
        labTest.setPrice(new BigDecimal("5000.00"));
        labTest.setCategoryTest(category);

        DetailsContrat savedDetail = new DetailsContrat();
        savedDetail.setLabTest(labTest);
        savedDetail.setAmountRemise(new BigDecimal("500.00"));
        savedDetail.setAmountAfterRemise(new BigDecimal("4500.00"));
        savedDetail.setCategoryTestId(CATEGORY_TEST_ID);

        when(contratRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contrat));
        when(labTestRepository.findById(LAB_TEST_ID)).thenReturn(Optional.of(labTest));
        when(detailsContratRepository.save(any())).thenReturn(savedDetail);
        when(contratMapper.toDetailsDto(savedDetail)).thenReturn(
                new DetailsContratDto(UUID.randomUUID(), LAB_TEST_ID, "Test ABC",
                        null, null, new BigDecimal("500.00"), new BigDecimal("4500.00"), CATEGORY_TEST_ID));

        TestDetailRequestDto dto = new TestDetailRequestDto();
        dto.setTestId(LAB_TEST_ID);
        dto.setAmountRemise(new BigDecimal("500.00"));
        dto.setAmountAfterRemise(new BigDecimal("4500.00"));

        DetailsContratDto result = service.addTestDetail(CONTRACT_ID, dto);

        ArgumentCaptor<DetailsContrat> captor = ArgumentCaptor.forClass(DetailsContrat.class);
        verify(detailsContratRepository).save(captor.capture());
        assertThat(captor.getValue().getCategoryTestId()).isEqualTo(CATEGORY_TEST_ID);
        assertThat(captor.getValue().getAmountRemise()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("close - isClose passe à true")
    void closeContract_setsIsCloseTrue() {
        Contrat contrat = buildContrat();
        ContratResponseDto mockDto = new ContratResponseDto(
                CONTRACT_ID, null, null, null, null, null, null, null,
                10, LocalDate.now(), null, "INACTIF", true, true,
                // branchId, createdAt, updatedAt, usedTestsCount, invoice.
                new ArrayList<>(), BRANCH_ID, null, null, null, null);

        when(contratRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contrat));
        when(contratRepository.save(any())).thenReturn(contrat);
        when(contratMapper.toResponseDto(contrat)).thenReturn(mockDto);

        service.close(CONTRACT_ID);

        ArgumentCaptor<Contrat> captor = ArgumentCaptor.forClass(Contrat.class);
        verify(contratRepository).save(captor.capture());
        assertThat(captor.getValue().getIsClose()).isTrue();
    }

    @Test
    @DisplayName("activate - déjà ACTIF → InvalidOperationException")
    void activate_alreadyActive_throws422() {
        Contrat contrat = buildContrat();
        contrat.setStatus("ACTIF");

        when(contratRepository.findById(CONTRACT_ID)).thenReturn(Optional.of(contrat));

        assertThatThrownBy(() -> service.activate(CONTRACT_ID))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("CONTRACT_ALREADY_ACTIVE");

        verify(invoiceRepository, never()).save(any());
    }
}

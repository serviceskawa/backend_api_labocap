package com.labo.anapath.patient;

import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.DuplicateResourceException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.finance.Invoice;
import com.labo.anapath.finance.InvoiceRepository;
import com.labo.anapath.finance.InvoiceStatus;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.testorder.TestOrderStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
class PatientServiceImplTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private PatientMapper patientMapper;

    @Mock
    private TestOrderRepository testOrderRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    @InjectMocks
    private PatientServiceImpl patientService;

    private final UUID BRANCH_ID = UUID.randomUUID();
    private final UUID PATIENT_ID = UUID.randomUUID();

    private Patient buildPatient() {
        Patient patient = new Patient();
        patient.setBranchId(BRANCH_ID);
        patient.setFirstname("Jean");
        patient.setLastname("Dupont");
        patient.setTelephone1("0600000001");
        patient.setGenre("M");
        patient.setBirthday(LocalDate.of(1990, 1, 1));
        return patient;
    }

    private PatientResponseDto buildResponseDto(Patient patient) {
        return new PatientResponseDto(
                PATIENT_ID, null, patient.getFirstname(), patient.getLastname(),
                patient.getGenre(), patient.getTelephone1(), null, null,
                null, null, patient.getBirthday(), null, null, null,
                BRANCH_ID, LocalDateTime.now(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    @DisplayName("findById - should return PatientResponseDto when patient exists")
    void findById_success() {
        Patient patient = buildPatient();
        PatientResponseDto dto = buildResponseDto(patient);
        when(patientRepository.findByIdAndBranchId(PATIENT_ID, BRANCH_ID)).thenReturn(Optional.of(patient));
        when(patientMapper.toResponseDto(patient)).thenReturn(dto);

        PatientResponseDto result = patientService.findById(PATIENT_ID, BRANCH_ID);

        assertThat(result).isNotNull();
        assertThat(result.firstname()).isEqualTo("Jean");
        verify(patientRepository).findByIdAndBranchId(PATIENT_ID, BRANCH_ID);
    }

    @Test
    @DisplayName("findById - should throw ResourceNotFoundException when patient not found")
    void findById_notFound() {
        when(patientRepository.findByIdAndBranchId(PATIENT_ID, BRANCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.findById(PATIENT_ID, BRANCH_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("create - should create patient successfully")
    void create_success() {
        PatientRequestDto requestDto = new PatientRequestDto();
        requestDto.setFirstname("Marie");
        requestDto.setLastname("Curie");
        requestDto.setTelephone1("0600000002");
        requestDto.setGenre("F");

        Patient patient = new Patient();
        patient.setFirstname("Marie");
        patient.setLastname("Curie");
        patient.setTelephone1("0600000002");
        patient.setGenre("F");

        PatientResponseDto responseDto = new PatientResponseDto(
                UUID.randomUUID(), null, "Marie", "Curie", "F",
                "0600000002", null, null, null, null, null,
                null, null, null, BRANCH_ID, LocalDateTime.now(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(patientRepository.existsByTelephone1AndBranchId("0600000002", BRANCH_ID)).thenReturn(false);
        when(patientMapper.toEntity(requestDto)).thenReturn(patient);
        when(patientRepository.save(any(Patient.class))).thenReturn(patient);
        when(patientMapper.toResponseDto(patient)).thenReturn(responseDto);

        PatientResponseDto result = patientService.create(requestDto, BRANCH_ID);

        assertThat(result).isNotNull();
        assertThat(result.firstname()).isEqualTo("Marie");
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    @DisplayName("create - should create patient without phone (phone is optional)")
    void create_noPhone_success() {
        PatientRequestDto requestDto = new PatientRequestDto();
        requestDto.setFirstname("Sans");
        requestDto.setLastname("Telephone");
        requestDto.setTelephone1(null);
        requestDto.setGenre("F");

        Patient patient = new Patient();
        patient.setFirstname("Sans");
        patient.setLastname("Telephone");
        patient.setTelephone1(null);
        patient.setGenre("F");

        PatientResponseDto responseDto = new PatientResponseDto(
                UUID.randomUUID(), null, "Sans", "Telephone", "F",
                null, null, null, null, null, null,
                null, null, null, BRANCH_ID, null,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(patientMapper.toEntity(requestDto)).thenReturn(patient);
        when(patientRepository.save(any(Patient.class))).thenReturn(patient);
        when(patientMapper.toResponseDto(patient)).thenReturn(responseDto);

        PatientResponseDto result = patientService.create(requestDto, BRANCH_ID);

        assertThat(result).isNotNull();
        assertThat(result.firstname()).isEqualTo("Sans");
        assertThat(result.telephone1()).isNull();
        verify(patientRepository).save(any(Patient.class));
    }

    @Test
    @DisplayName("create - should throw DuplicateResourceException when phone already exists")
    void create_duplicatePhone() {
        PatientRequestDto requestDto = new PatientRequestDto();
        requestDto.setFirstname("Marie");
        requestDto.setLastname("Curie");
        requestDto.setTelephone1("0600000002");
        requestDto.setGenre("F");

        when(patientRepository.existsByTelephone1AndBranchId("0600000002", BRANCH_ID)).thenReturn(true);

        assertThatThrownBy(() -> patientService.create(requestDto, BRANCH_ID))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("update - should update patient successfully")
    void update_success() {
        Patient patient = buildPatient();
        PatientRequestDto requestDto = new PatientRequestDto();
        requestDto.setFirstname("Jean-Paul");
        requestDto.setLastname("Dupont");
        requestDto.setTelephone1("0600000001");
        requestDto.setGenre("M");

        PatientResponseDto responseDto = new PatientResponseDto(
                PATIENT_ID, null, "Jean-Paul", "Dupont", "M",
                "0600000001", null, null, null, null, null,
                null, null, null, BRANCH_ID, LocalDateTime.now(),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        when(patientRepository.findByIdAndBranchId(PATIENT_ID, BRANCH_ID)).thenReturn(Optional.of(patient));
        when(patientRepository.save(any(Patient.class))).thenReturn(patient);
        when(patientMapper.toResponseDto(patient)).thenReturn(responseDto);

        PatientResponseDto result = patientService.update(PATIENT_ID, requestDto, BRANCH_ID);

        assertThat(result).isNotNull();
        verify(patientRepository).save(patient);
    }

    @Test
    @DisplayName("update - code en doublon sur autre patient → DuplicateResourceException")
    void update_duplicateCode_throws409() {
        Patient patient = buildPatient();
        patient.setCode("CODE-001");

        PatientRequestDto requestDto = new PatientRequestDto();
        requestDto.setFirstname("Jean");
        requestDto.setLastname("Dupont");
        requestDto.setCode("CODE-DEJA-PRIS");

        when(patientRepository.findByIdAndBranchId(PATIENT_ID, BRANCH_ID)).thenReturn(Optional.of(patient));
        when(patientRepository.existsByCodeAndBranchIdAndIdNot("CODE-DEJA-PRIS", BRANCH_ID, PATIENT_ID))
                .thenReturn(true);

        assertThatThrownBy(() -> patientService.update(PATIENT_ID, requestDto, BRANCH_ID))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("CODE-DEJA-PRIS");

        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    @DisplayName("delete - should soft delete patient when no linked orders")
    void delete_noTestOrders_softDeletes() {
        Patient patient = buildPatient();
        when(patientRepository.findByIdAndBranchId(PATIENT_ID, BRANCH_ID)).thenReturn(Optional.of(patient));
        when(testOrderRepository.existsByPatient(patient)).thenReturn(false);

        patientService.delete(PATIENT_ID, BRANCH_ID);

        verify(patientRepository).delete(patient);
    }

    @Test
    @DisplayName("delete - should throw BusinessException when patient has linked test orders")
    void delete_withTestOrders_throwsBusinessException() {
        Patient patient = buildPatient();
        when(patientRepository.findByIdAndBranchId(PATIENT_ID, BRANCH_ID)).thenReturn(Optional.of(patient));
        when(testOrderRepository.existsByPatient(patient)).thenReturn(true);

        assertThatThrownBy(() -> patientService.delete(PATIENT_ID, BRANCH_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PATIENT_HAS_ORDERS");
    }

    @Test
    @DisplayName("delete - should throw ResourceNotFoundException when patient not found")
    void delete_notFound() {
        when(patientRepository.findByIdAndBranchId(PATIENT_ID, BRANCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.delete(PATIENT_ID, BRANCH_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getProfile - should return aggregated profile dto")
    void getProfile_found_returnsAggregatedDto() {
        Patient patient = buildPatient();
        PatientResponseDto patientDto = buildResponseDto(patient);

        TestOrder order1 = new TestOrder();
        order1.setStatus(TestOrderStatus.VALIDATED);
        TestOrder order2 = new TestOrder();
        order2.setStatus(TestOrderStatus.PENDING);

        Invoice invoice1 = new Invoice();
        invoice1.setTotal(new BigDecimal("50000"));
        invoice1.setStatus(InvoiceStatus.PAID);
        Invoice invoice2 = new Invoice();
        invoice2.setTotal(new BigDecimal("30000"));
        invoice2.setStatus(InvoiceStatus.PENDING);

        when(patientRepository.findByIdAndBranchId(PATIENT_ID, BRANCH_ID)).thenReturn(Optional.of(patient));
        when(patientMapper.toResponseDto(patient)).thenReturn(patientDto);
        when(testOrderRepository.findByPatientOrderByCreatedAtDesc(patient)).thenReturn(List.of(order1, order2));
        when(invoiceRepository.findByPatientOrderByCreatedAtDesc(patient)).thenReturn(List.of(invoice1, invoice2));

        PatientProfileDto profile = patientService.getProfile(PATIENT_ID, BRANCH_ID);

        assertThat(profile).isNotNull();
        assertThat(profile.totalOrders()).isEqualTo(2);
        assertThat(profile.pendingOrders()).isEqualTo(1);
        assertThat(profile.completedOrders()).isEqualTo(1);
        assertThat(profile.totalInvoiced()).isEqualByComparingTo(new BigDecimal("80000"));
        assertThat(profile.totalPaid()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(profile.totalUnpaid()).isEqualByComparingTo(new BigDecimal("30000"));
    }

    @Test
    @DisplayName("getProfile - should throw ResourceNotFoundException when patient not found")
    void getProfile_notFound() {
        when(patientRepository.findByIdAndBranchId(PATIENT_ID, BRANCH_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getProfile(PATIENT_ID, BRANCH_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

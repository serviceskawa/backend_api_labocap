package com.labo.anapath.prestationorder;

import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.patient.Patient;
import com.labo.anapath.patient.PatientRepository;
import com.labo.anapath.prestation.Prestation;
import com.labo.anapath.prestation.PrestationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrestationOrderServiceImplTest {

    @Mock private PrestationOrderRepository repository;
    @Mock private PrestationRepository prestationRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private PrestationOrderMapper mapper;

    @InjectMocks private PrestationOrderServiceImpl service;

    private final UUID BRANCH_ID = UUID.randomUUID();
    private final UUID PRESTATION_ID = UUID.randomUUID();
    private final UUID PATIENT_ID = UUID.randomUUID();

    @Test
    @DisplayName("create - total copié depuis prestation.price (pas du body)")
    void create_shouldUsePrestationPriceAsTotal() {
        Prestation prestation = new Prestation();
        prestation.setId(PRESTATION_ID);
        prestation.setPrice(new BigDecimal("12500.00"));

        Patient patient = new Patient();
        patient.setId(PATIENT_ID);
        patient.setFirstname("Jean");
        patient.setLastname("DUPONT");

        PrestationOrder saved = new PrestationOrder();
        saved.setPrestation(prestation);
        saved.setPatient(patient);
        saved.setTotal(new BigDecimal("12500.00"));

        PrestationOrderRequestDto dto = new PrestationOrderRequestDto();
        dto.setPrestationId(PRESTATION_ID);
        dto.setPatientId(PATIENT_ID);

        PrestationOrderResponseDto responseDto = new PrestationOrderResponseDto(
                UUID.randomUUID(), PATIENT_ID, "Jean DUPONT",
                PRESTATION_ID, "Consultation", new BigDecimal("12500.00"),
                "Nouveau", BRANCH_ID, null);

        when(prestationRepository.findById(PRESTATION_ID)).thenReturn(Optional.of(prestation));
        when(patientRepository.findByIdAndBranchId(eq(PATIENT_ID), any())).thenReturn(Optional.of(patient));
        when(repository.save(any())).thenReturn(saved);
        when(mapper.toResponseDto(saved)).thenReturn(responseDto);

        PrestationOrderResponseDto result = service.create(dto, BRANCH_ID);

        ArgumentCaptor<PrestationOrder> captor = ArgumentCaptor.forClass(PrestationOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTotal()).isEqualByComparingTo(new BigDecimal("12500.00"));
        assertThat(captor.getValue().getStatus()).isEqualTo("Nouveau");
    }

    @Test
    @DisplayName("create - prestation introuvable → ResourceNotFoundException")
    void create_prestationNotFound_throws() {
        PrestationOrderRequestDto dto = new PrestationOrderRequestDto();
        dto.setPrestationId(PRESTATION_ID);
        dto.setPatientId(PATIENT_ID);

        when(prestationRepository.findById(PRESTATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(dto, BRANCH_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("create - patient introuvable → ResourceNotFoundException")
    void create_patientNotFound_throws() {
        Prestation prestation = new Prestation();
        prestation.setId(PRESTATION_ID);
        prestation.setPrice(new BigDecimal("5000.00"));

        PrestationOrderRequestDto dto = new PrestationOrderRequestDto();
        dto.setPrestationId(PRESTATION_ID);
        dto.setPatientId(PATIENT_ID);

        when(prestationRepository.findById(PRESTATION_ID)).thenReturn(Optional.of(prestation));
        when(patientRepository.findByIdAndBranchId(eq(PATIENT_ID), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(dto, BRANCH_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("delete - order introuvable → ResourceNotFoundException")
    void delete_orderNotFound_throws() {
        UUID orderId = UUID.randomUUID();
        when(repository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(orderId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("create - statut initial est 'Nouveau'")
    void create_initialStatusIsNouveau() {
        Prestation prestation = new Prestation();
        prestation.setId(PRESTATION_ID);
        prestation.setPrice(new BigDecimal("3000.00"));

        Patient patient = new Patient();
        patient.setId(PATIENT_ID);
        patient.setFirstname("Marie");
        patient.setLastname("KONE");

        PrestationOrder saved = new PrestationOrder();

        PrestationOrderRequestDto dto = new PrestationOrderRequestDto();
        dto.setPrestationId(PRESTATION_ID);
        dto.setPatientId(PATIENT_ID);

        when(prestationRepository.findById(PRESTATION_ID)).thenReturn(Optional.of(prestation));
        when(patientRepository.findByIdAndBranchId(eq(PATIENT_ID), any())).thenReturn(Optional.of(patient));
        when(repository.save(any())).thenReturn(saved);
        when(mapper.toResponseDto(saved)).thenReturn(
                new PrestationOrderResponseDto(UUID.randomUUID(), PATIENT_ID, "Marie KONE",
                        PRESTATION_ID, "Test", new BigDecimal("3000.00"), "Nouveau", BRANCH_ID, null));

        service.create(dto, BRANCH_ID);

        ArgumentCaptor<PrestationOrder> captor = ArgumentCaptor.forClass(PrestationOrder.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("Nouveau");
    }
}

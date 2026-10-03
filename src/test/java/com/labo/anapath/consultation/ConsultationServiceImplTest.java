package com.labo.anapath.consultation;

import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.storage.FileStorageService;
import com.labo.anapath.patient.Patient;
import com.labo.anapath.patient.PatientRepository;
import com.labo.anapath.prestation.Prestation;
import com.labo.anapath.prestation.PrestationRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultationServiceImplTest {

    @Mock private ConsultationRepository consultationRepository;
    @Mock private ConsultationFileRepository consultationFileRepository;
    @Mock private TypeConsultationRepository typeConsultationRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private PrestationRepository prestationRepository;
    @Mock private UserRepository userRepository;
    @Mock private ConsultationMapper consultationMapper;
    @Mock private FileStorageService fileStorageService;
    @Mock private com.labo.anapath.common.storage.FichierStockeRepository fichiers;

    @InjectMocks private ConsultationServiceImpl service;

    private final UUID BRANCH_ID = UUID.randomUUID();
    private final UUID PATIENT_ID = UUID.randomUUID();
    private final UUID PRESTATION_ID = UUID.randomUUID();
    private final UUID CONSULTATION_ID = UUID.randomUUID();

    private Patient buildPatient() {
        Patient p = new Patient();
        p.setId(PATIENT_ID);
        p.setFirstname("Jean");
        p.setLastname("DUPONT");
        p.setCode("PAT0001");
        return p;
    }

    private Prestation buildPrestation(BigDecimal price) {
        Prestation pr = new Prestation();
        pr.setId(PRESTATION_ID);
        pr.setName("Consultation Standard");
        pr.setPrice(price);
        return pr;
    }

    @Test
    @DisplayName("create - génère code CON0001 pour la première consultation")
    void create_shouldGenerateCodeCON0001() {
        Patient patient = buildPatient();
        Prestation prestation = buildPrestation(new BigDecimal("5000.00"));

        ConsultationRequestDto dto = new ConsultationRequestDto();
        dto.setPatientId(PATIENT_ID);
        dto.setPrestationId(PRESTATION_ID);
        dto.setDate(LocalDateTime.now());

        Consultation saved = new Consultation();
        saved.setCode("CON0001");

        when(consultationRepository.countByBranchId(BRANCH_ID)).thenReturn(0L);
        when(prestationRepository.findById(PRESTATION_ID)).thenReturn(Optional.of(prestation));
        when(patientRepository.findByIdAndBranchId(eq(PATIENT_ID), any())).thenReturn(Optional.of(patient));
        when(consultationRepository.save(any())).thenReturn(saved);
        when(consultationMapper.toResponseDto(saved)).thenReturn(
                new ConsultationResponseDto(UUID.randomUUID(), "CON0001", PATIENT_ID, "Jean", "DUPONT",
                        null, null, null, null, PRESTATION_ID, "Consultation Standard",
                        null, null, null, null, null, null, null, null,
                        new BigDecimal("5000.00"), "pending", "espèce",
                        LocalDateTime.now(), null, BRANCH_ID, LocalDateTime.now()));

        var result = service.create(dto, BRANCH_ID);

        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("CON0001");
    }

    @Test
    @DisplayName("create - fees depuis prestation.price (pas du body)")
    void create_shouldSetFeesFromPrestation() {
        Patient patient = buildPatient();
        Prestation prestation = buildPrestation(new BigDecimal("12500.00"));

        ConsultationRequestDto dto = new ConsultationRequestDto();
        dto.setPatientId(PATIENT_ID);
        dto.setPrestationId(PRESTATION_ID);
        dto.setDate(LocalDateTime.now());

        Consultation saved = new Consultation();

        when(consultationRepository.countByBranchId(BRANCH_ID)).thenReturn(5L);
        when(prestationRepository.findById(PRESTATION_ID)).thenReturn(Optional.of(prestation));
        when(patientRepository.findByIdAndBranchId(eq(PATIENT_ID), any())).thenReturn(Optional.of(patient));
        when(consultationRepository.save(any())).thenReturn(saved);
        when(consultationMapper.toResponseDto(saved)).thenReturn(null);

        service.create(dto, BRANCH_ID);

        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(captor.capture());
        assertThat(captor.getValue().getFees()).isEqualByComparingTo(new BigDecimal("12500.00"));
    }

    @Test
    @DisplayName("create - statut par défaut est 'pending'")
    void create_shouldDefaultStatusToPending() {
        Patient patient = buildPatient();
        Prestation prestation = buildPrestation(new BigDecimal("3000.00"));

        ConsultationRequestDto dto = new ConsultationRequestDto();
        dto.setPatientId(PATIENT_ID);
        dto.setPrestationId(PRESTATION_ID);
        dto.setDate(LocalDateTime.now());

        Consultation saved = new Consultation();

        when(consultationRepository.countByBranchId(BRANCH_ID)).thenReturn(0L);
        when(prestationRepository.findById(PRESTATION_ID)).thenReturn(Optional.of(prestation));
        when(patientRepository.findByIdAndBranchId(eq(PATIENT_ID), any())).thenReturn(Optional.of(patient));
        when(consultationRepository.save(any())).thenReturn(saved);
        when(consultationMapper.toResponseDto(saved)).thenReturn(null);

        service.create(dto, BRANCH_ID);

        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("updateByDoctor - ne modifie que les 5 champs médicaux")
    void updateByDoctor_shouldOnlyUpdateMedicalFields() {
        Consultation consultation = new Consultation();
        consultation.setId(CONSULTATION_ID);
        consultation.setCode("CON0001");
        consultation.setFees(new BigDecimal("5000.00")); // ne doit pas changer
        Patient patient = buildPatient();
        consultation.setPatient(patient);

        ConsultationDoctorUpdateDto dto = new ConsultationDoctorUpdateDto();
        dto.setMotif("Douleurs abdominales");
        dto.setAnamnese("Depuis 3 jours");
        dto.setExamenPhysique("Abdomen sensible");
        dto.setDiagnostic("Gastrite");
        dto.setAntecedent("Aucun");

        when(consultationRepository.findById(CONSULTATION_ID)).thenReturn(Optional.of(consultation));
        when(consultationRepository.save(any())).thenReturn(consultation);
        when(consultationMapper.toResponseDto(consultation)).thenReturn(null);

        service.updateByDoctor(CONSULTATION_ID, dto, null);

        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(captor.capture());
        assertThat(captor.getValue().getMotif()).isEqualTo("Douleurs abdominales");
        assertThat(captor.getValue().getAnamnese()).isEqualTo("Depuis 3 jours");
        assertThat(captor.getValue().getFees()).isEqualByComparingTo(new BigDecimal("5000.00")); // inchangé
    }

    @Test
    @DisplayName("updateType - ne modifie que le type de consultation")
    void updateType_shouldOnlyUpdateTypeConsultationId() {
        UUID typeId = UUID.randomUUID();
        TypeConsultation type = new TypeConsultation();
        type.setId(typeId);
        type.setName("Consultation générale");

        Consultation consultation = new Consultation();
        consultation.setId(CONSULTATION_ID);

        when(consultationRepository.findById(CONSULTATION_ID)).thenReturn(Optional.of(consultation));
        when(typeConsultationRepository.findById(typeId)).thenReturn(Optional.of(type));
        when(consultationRepository.save(any())).thenReturn(consultation);
        when(consultationMapper.toResponseDto(consultation)).thenReturn(null);

        service.updateType(CONSULTATION_ID, typeId);

        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(captor.capture());
        assertThat(captor.getValue().getTypeConsultation().getId()).isEqualTo(typeId);
    }

    @Test
    @DisplayName("findAll - consultation introuvable → ResourceNotFoundException")
    void findById_notFound_throws() {
        when(consultationRepository.findById(CONSULTATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(CONSULTATION_ID, BRANCH_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("create - doctorId → attribuateDoctor (User), pas doctor (Doctor) — AC 4-2")
    void create_doctorId_setsAttribuateDoctor() {
        Patient patient = buildPatient();
        Prestation prestation = buildPrestation(new BigDecimal("5000.00"));
        UUID doctorUserId = UUID.randomUUID();

        User doctorUser = new User();
        doctorUser.setId(doctorUserId);
        doctorUser.setFirstname("Dr");
        doctorUser.setLastname("Medecin");

        ConsultationRequestDto dto = new ConsultationRequestDto();
        dto.setPatientId(PATIENT_ID);
        dto.setPrestationId(PRESTATION_ID);
        dto.setDoctorId(doctorUserId);
        dto.setDate(LocalDateTime.now());

        Consultation saved = new Consultation();

        when(consultationRepository.countByBranchId(BRANCH_ID)).thenReturn(0L);
        when(prestationRepository.findById(PRESTATION_ID)).thenReturn(Optional.of(prestation));
        when(patientRepository.findByIdAndBranchId(eq(PATIENT_ID), any())).thenReturn(Optional.of(patient));
        when(userRepository.findById(doctorUserId)).thenReturn(Optional.of(doctorUser));
        when(consultationRepository.save(any())).thenReturn(saved);
        when(consultationMapper.toResponseDto(saved)).thenReturn(null);

        service.create(dto, BRANCH_ID);

        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(captor.capture());
        assertThat(captor.getValue().getAttribuateDoctor()).isEqualTo(doctorUser);
        assertThat(captor.getValue().getDoctor()).isNull();
    }

    @Test
    @DisplayName("create - prestation introuvable → ResourceNotFoundException")
    void create_prestationNotFound_throws() {
        ConsultationRequestDto dto = new ConsultationRequestDto();
        dto.setPatientId(PATIENT_ID);
        dto.setPrestationId(PRESTATION_ID);
        dto.setDate(LocalDateTime.now());

        when(consultationRepository.countByBranchId(BRANCH_ID)).thenReturn(0L);
        when(prestationRepository.findById(PRESTATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(dto, BRANCH_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

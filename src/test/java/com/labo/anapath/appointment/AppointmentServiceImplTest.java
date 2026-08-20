package com.labo.anapath.appointment;

import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.consultation.Consultation;
import com.labo.anapath.consultation.ConsultationMapper;
import com.labo.anapath.consultation.ConsultationRepository;
import com.labo.anapath.consultation.ConsultationResponseDto;
import com.labo.anapath.patient.Patient;
import com.labo.anapath.patient.PatientRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceImplTest {

    @Mock private AppointmentRepository appointmentRepository;
    @Mock private PatientRepository patientRepository;
    @Mock private UserRepository userRepository;
    @Mock private ConsultationRepository consultationRepository;
    @Mock private ConsultationMapper consultationMapper;
    @Mock private AppointmentMapper appointmentMapper;

    @InjectMocks private AppointmentServiceImpl service;

    private final UUID BRANCH_ID = UUID.randomUUID();
    private final UUID PATIENT_ID = UUID.randomUUID();
    private final UUID APPOINTMENT_ID = UUID.randomUUID();

    private Patient buildPatient() {
        Patient p = new Patient();
        p.setId(PATIENT_ID);
        p.setFirstname("Jean");
        p.setLastname("DUPONT");
        return p;
    }

    private Appointment buildAppointmentWithDoctor() {
        User doctor = new User();
        doctor.setId(UUID.randomUUID());
        doctor.setFirstname("Marie");
        doctor.setLastname("CURIE");

        Appointment a = new Appointment();
        a.setId(APPOINTMENT_ID);
        a.setBranchId(BRANCH_ID);
        a.setPatient(buildPatient());
        a.setDoctorInterne(doctor);
        a.setPriority("normal");
        a.setDate(LocalDateTime.now());
        return a;
    }

    @Test
    @DisplayName("create - statut par défaut est 'pending'")
    void create_shouldSetDefaultStatusPending() {
        AppointmentRequestDto dto = new AppointmentRequestDto();
        dto.setPatientId(PATIENT_ID);
        dto.setTime(LocalDateTime.now());

        Appointment saved = new Appointment();

        when(patientRepository.findById(PATIENT_ID)).thenReturn(Optional.of(buildPatient()));
        when(appointmentRepository.save(any())).thenReturn(saved);
        when(appointmentMapper.toResponseDto(saved)).thenReturn(null);

        service.create(dto, BRANCH_ID);

        ArgumentCaptor<Appointment> captor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("pending");
    }

    @Test
    @DisplayName("getCalendar - title construit avec le nom du médecin interne")
    void getCalendar_shouldBuildTitleWithDoctorName() {
        Appointment a = buildAppointmentWithDoctor();
        when(appointmentRepository.findByBranchId(BRANCH_ID)).thenReturn(List.of(a));

        List<AppointmentCalendarDto> result = service.getCalendar(BRANCH_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).title()).isEqualTo("RDV CURIE Marie");
        // Nom puis prénoms, comme partout ailleurs depuis NomComplet.
        assertThat(result.get(0).doctorName()).isEqualTo("CURIE Marie");
    }

    @Test
    @DisplayName("getCalendar - title vide si aucun médecin assigné")
    void getCalendar_shouldReturnEmptyTitle_whenNoDoctorAssigned() {
        Appointment a = new Appointment();
        a.setId(APPOINTMENT_ID);
        a.setPatient(buildPatient());
        a.setDoctorInterne(null);
        a.setPriority("normal");

        when(appointmentRepository.findByBranchId(BRANCH_ID)).thenReturn(List.of(a));

        List<AppointmentCalendarDto> result = service.getCalendar(BRANCH_ID);

        assertThat(result.get(0).title()).isEmpty();
        assertThat(result.get(0).doctorId()).isNull();
    }

    @Test
    @DisplayName("getCalendar - priority 'normal' → className 'bg-primary'")
    void getClassNameFromPriority_normal_returnsBgPrimary() {
        Appointment a = buildAppointmentWithDoctor();
        a.setPriority("normal");
        when(appointmentRepository.findByBranchId(BRANCH_ID)).thenReturn(List.of(a));

        List<AppointmentCalendarDto> result = service.getCalendar(BRANCH_ID);

        assertThat(result.get(0).className()).isEqualTo("bg-primary");
    }

    @Test
    @DisplayName("getCalendar - priority 'urgent' → className 'bg-warning'")
    void getClassNameFromPriority_urgent_returnsBgWarning() {
        Appointment a = buildAppointmentWithDoctor();
        a.setPriority("urgent");
        when(appointmentRepository.findByBranchId(BRANCH_ID)).thenReturn(List.of(a));

        List<AppointmentCalendarDto> result = service.getCalendar(BRANCH_ID);

        assertThat(result.get(0).className()).isEqualTo("bg-warning");
    }

    @Test
    @DisplayName("getCalendar - priority 'tres urgent' → className 'bg-danger'")
    void getClassNameFromPriority_tresUrgent_returnsBgDanger() {
        Appointment a = buildAppointmentWithDoctor();
        a.setPriority("tres urgent");
        when(appointmentRepository.findByBranchId(BRANCH_ID)).thenReturn(List.of(a));

        List<AppointmentCalendarDto> result = service.getCalendar(BRANCH_ID);

        assertThat(result.get(0).className()).isEqualTo("bg-danger");
    }

    @Test
    @DisplayName("createConsultationFromAppointment - idempotent si consultation déjà existante")
    void createConsultation_shouldBeIdempotent() {
        Consultation existing = new Consultation();
        ConsultationResponseDto existingDto = new ConsultationResponseDto(
                UUID.randomUUID(), "CON0001", PATIENT_ID, "Jean", "DUPONT",
                null, null, null, null, null, null,
                null, null, null, null, null, null, null, null,
                null, "pending", "espèce", LocalDateTime.now(), null, BRANCH_ID, LocalDateTime.now());

        Appointment appointment = buildAppointmentWithDoctor();
        appointment.setConsultation(existing);

        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
        when(consultationMapper.toResponseDto(existing)).thenReturn(existingDto);

        ConsultationResponseDto result = service.createConsultationFromAppointment(APPOINTMENT_ID);

        verify(consultationRepository, never()).save(any());
        assertThat(result).isEqualTo(existingDto);
    }

    @Test
    @DisplayName("createConsultationFromAppointment - copie patient et médecin depuis le RDV")
    void createConsultation_shouldCopyPatientAndDoctorFromAppointment() {
        Appointment appointment = buildAppointmentWithDoctor();
        appointment.setConsultation(null);

        when(appointmentRepository.findById(APPOINTMENT_ID)).thenReturn(Optional.of(appointment));
        when(consultationRepository.countByBranchId(BRANCH_ID)).thenReturn(0L);
        when(consultationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(consultationMapper.toResponseDto(any())).thenReturn(null);

        service.createConsultationFromAppointment(APPOINTMENT_ID);

        ArgumentCaptor<Consultation> captor = ArgumentCaptor.forClass(Consultation.class);
        verify(consultationRepository).save(captor.capture());
        assertThat(captor.getValue().getPatient().getId()).isEqualTo(PATIENT_ID);
        assertThat(captor.getValue().getAttribuateDoctor()).isEqualTo(appointment.getDoctorInterne());
        assertThat(captor.getValue().getCode()).isEqualTo("CON0001");
        assertThat(captor.getValue().getStatus()).isEqualTo("pending");
        assertThat(captor.getValue().getPaymentMode()).isEqualTo("espèce");
    }
}

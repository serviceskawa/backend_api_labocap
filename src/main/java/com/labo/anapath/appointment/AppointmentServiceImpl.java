package com.labo.anapath.appointment;

import com.labo.anapath.common.NomComplet;

import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.consultation.Consultation;
import com.labo.anapath.consultation.ConsultationMapper;
import com.labo.anapath.consultation.ConsultationRepository;
import com.labo.anapath.consultation.ConsultationResponseDto;
import com.labo.anapath.patient.PatientRepository;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentServiceImpl implements AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final ConsultationRepository consultationRepository;
    private final ConsultationMapper consultationMapper;
    private final AppointmentMapper appointmentMapper;

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentCalendarDto> getCalendar(UUID branchId) {
        return appointmentRepository.findByBranchId(branchId).stream()
                .map(a -> {
                    String doctorName = a.getDoctorInterne() != null
                            ? NomComplet.de(a.getDoctorInterne().getLastname(), a.getDoctorInterne().getFirstname())
                            : "";
                    String title = a.getDoctorInterne() != null ? "RDV " + doctorName : "";
                    String className = toClassName(a.getPriority());
                    return new AppointmentCalendarDto(
                            a.getId(), title, a.getDate(),
                            a.getDoctorInterne() != null ? a.getDoctorInterne().getId() : null,
                            doctorName, className);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentResponseDto findById(UUID id, UUID branchId) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
        if (!appointment.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Appointment", id);
        }
        return appointmentMapper.toResponseDto(appointment);
    }

    @Override
    @Transactional
    public AppointmentResponseDto create(AppointmentRequestDto dto, UUID branchId) {
        Appointment appointment = new Appointment();
        appointment.setBranchId(branchId);
        appointment.setPatient(patientRepository.findById(dto.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", dto.getPatientId())));
        if (dto.getDoctorId() != null) {
            appointment.setDoctorInterne(userRepository.findById(dto.getDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", dto.getDoctorId())));
        }
        appointment.setDate(dto.getTime());
        appointment.setPriority(dto.getPriority() != null ? dto.getPriority() : "normal");
        appointment.setStatus("pending");
        appointment.setMessage(dto.getMessage());
        return appointmentMapper.toResponseDto(appointmentRepository.save(appointment));
    }

    @Override
    @Transactional
    public AppointmentResponseDto update(UUID id, AppointmentRequestDto dto, UUID branchId) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
        if (!appointment.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Appointment", id);
        }
        appointment.setPatient(patientRepository.findById(dto.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", dto.getPatientId())));
        if (dto.getDoctorId() != null) {
            appointment.setDoctorInterne(userRepository.findById(dto.getDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("User", dto.getDoctorId())));
        } else {
            appointment.setDoctorInterne(null);
        }
        appointment.setDate(dto.getTime());
        appointment.setPriority(dto.getPriority());
        appointment.setMessage(dto.getMessage());
        return appointmentMapper.toResponseDto(appointmentRepository.save(appointment));
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID branchId) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", id));
        if (!appointment.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Appointment", id);
        }
        appointmentRepository.delete(appointment);
    }

    @Override
    @Transactional
    public ConsultationResponseDto createConsultationFromAppointment(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Appointment", appointmentId));

        if (appointment.getConsultation() != null) {
            return consultationMapper.toResponseDto(appointment.getConsultation());
        }

        long count = consultationRepository.countByBranchId(appointment.getBranchId());
        String code = "CON" + String.format("%04d", count + 1);

        Consultation consultation = new Consultation();
        consultation.setBranchId(appointment.getBranchId());
        consultation.setCode(code);
        consultation.setPatient(appointment.getPatient());
        consultation.setDate(appointment.getDate());
        consultation.setAppointment(appointment);
        consultation.setStatus("pending");
        consultation.setPaymentMode("espèce");

        if (appointment.getDoctorInterne() != null) {
            consultation.setAttribuateDoctor(appointment.getDoctorInterne());
        }

        return consultationMapper.toResponseDto(consultationRepository.save(consultation));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasConsultation(UUID appointmentId) {
        return appointmentRepository.findById(appointmentId)
                .map(a -> a.getConsultation() != null)
                .orElse(false);
    }

    private String toClassName(String priority) {
        // Réplique Laravel randColor() : normal -> bg-info, urgent -> bg-warning, très urgent -> bg-danger.
        if (priority == null) return "bg-info";
        return switch (priority) {
            case "urgent"      -> "bg-warning";
            case "tres urgent" -> "bg-danger";
            default            -> "bg-info";
        };
    }
}

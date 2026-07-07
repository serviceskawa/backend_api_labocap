package com.labo.anapath.consultation;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.storage.FileStorageService;
import com.labo.anapath.patient.PatientRepository;
import com.labo.anapath.prestation.Prestation;
import com.labo.anapath.prestation.PrestationRepository;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsultationServiceImpl implements ConsultationService {

    private final ConsultationRepository consultationRepository;
    private final ConsultationFileRepository consultationFileRepository;
    private final TypeConsultationRepository typeConsultationRepository;
    private final PatientRepository patientRepository;
    private final PrestationRepository prestationRepository;
    private final UserRepository userRepository;
    private final ConsultationMapper consultationMapper;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ConsultationResponseDto> findAll(int page, int size, UUID branchId,
                                                          UUID patientId, String status, UUID doctorId) {
        var pageable = PageRequest.of(page, size, Sort.by("date").descending());
        if (patientId != null || status != null || doctorId != null) {
            return PageResponse.of(consultationRepository
                    .findWithFilters(branchId, patientId, status, doctorId, pageable)
                    .map(consultationMapper::toResponseDto));
        }
        return PageResponse.of(consultationRepository.findByBranchId(branchId, pageable)
                .map(consultationMapper::toResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public ConsultationResponseDto findById(UUID id, UUID branchId) {
        Consultation consultation = consultationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id));
        if (!consultation.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Consultation", id);
        }
        return consultationMapper.toResponseDto(consultation);
    }

    @Override
    @Transactional
    public ConsultationResponseDto create(ConsultationRequestDto dto, UUID branchId) {
        long count = consultationRepository.countByBranchId(branchId);
        String code = "CON" + String.format("%04d", count + 1);

        Prestation prestation = prestationRepository.findById(dto.getPrestationId())
                .orElseThrow(() -> new ResourceNotFoundException("Prestation", dto.getPrestationId()));

        Consultation consultation = new Consultation();
        consultation.setBranchId(branchId);
        consultation.setCode(code);
        consultation.setPatient(patientRepository.findById(dto.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", dto.getPatientId())));
        consultation.setPrestation(prestation);
        consultation.setFees(prestation.getPrice()); // RÈGLE: fees toujours depuis prestation.price
        consultation.setStatus(dto.getStatus() != null ? dto.getStatus() : "pending");
        consultation.setPaymentMode(dto.getPaymentMode() != null ? dto.getPaymentMode() : "espèce");
        consultation.setDate(dto.getDate());
        consultation.setMotif(dto.getMotif());
        consultation.setNotes(dto.getNotes());
        consultation.setNextAppointment(dto.getNextAppointment());

        if (dto.getTypeConsultationId() != null) {
            consultation.setTypeConsultation(typeConsultationRepository.findById(dto.getTypeConsultationId())
                    .orElseThrow(() -> new ResourceNotFoundException("TypeConsultation", dto.getTypeConsultationId())));
        }
        if (dto.getDoctorId() != null) {
            consultation.setAttribuateDoctor(userRepository.findById(dto.getDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("User/Doctor", dto.getDoctorId())));
        }
        return consultationMapper.toResponseDto(consultationRepository.save(consultation));
    }

    @Override
    @Transactional
    public ConsultationResponseDto update(UUID id, ConsultationRequestDto dto, List<MultipartFile> files) {
        Consultation consultation = consultationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id));

        Prestation prestation = prestationRepository.findById(dto.getPrestationId())
                .orElseThrow(() -> new ResourceNotFoundException("Prestation", dto.getPrestationId()));

        consultation.setPatient(patientRepository.findById(dto.getPatientId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient", dto.getPatientId())));
        consultation.setPrestation(prestation);
        consultation.setFees(prestation.getPrice());
        consultation.setDate(dto.getDate());
        consultation.setStatus(dto.getStatus());
        consultation.setPaymentMode(dto.getPaymentMode());
        consultation.setNextAppointment(dto.getNextAppointment());
        consultation.setMotif(dto.getMotif());
        consultation.setNotes(dto.getNotes());

        if (dto.getDoctorId() != null) {
            consultation.setAttribuateDoctor(userRepository.findById(dto.getDoctorId())
                    .orElseThrow(() -> new ResourceNotFoundException("User/Doctor", dto.getDoctorId())));
        }
        if (dto.getTypeConsultationId() != null) {
            consultation.setTypeConsultation(typeConsultationRepository.findById(dto.getTypeConsultationId())
                    .orElseThrow(() -> new ResourceNotFoundException("TypeConsultation", dto.getTypeConsultationId())));
        }

        storeFiles(consultation, files);
        return consultationMapper.toResponseDto(consultationRepository.save(consultation));
    }

    @Override
    @Transactional
    public ConsultationResponseDto updateByDoctor(UUID id, ConsultationDoctorUpdateDto dto,
                                                   List<MultipartFile> files) {
        Consultation consultation = consultationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id));
        consultation.setMotif(dto.getMotif());
        consultation.setAnamnese(dto.getAnamnese());
        consultation.setExamenPhysique(dto.getExamenPhysique());
        consultation.setDiagnostic(dto.getDiagnostic());
        consultation.setAntecedent(dto.getAntecedent());
        storeFiles(consultation, files);
        return consultationMapper.toResponseDto(consultationRepository.save(consultation));
    }

    @Override
    @Transactional
    public ConsultationResponseDto updateType(UUID id, UUID typeConsultationId) {
        Consultation consultation = consultationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id));
        consultation.setTypeConsultation(typeConsultationRepository.findById(typeConsultationId)
                .orElseThrow(() -> new ResourceNotFoundException("TypeConsultation", typeConsultationId)));
        return consultationMapper.toResponseDto(consultationRepository.save(consultation));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConsultationFileResponseDto> getFiles(UUID id) {
        return consultationFileRepository.findByConsultationIdOrderByCreatedAtAsc(id)
                .stream()
                .map(f -> new ConsultationFileResponseDto(
                        f.getId(), f.getTypeFileLabel(), f.getPath(), f.getComment(), f.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Consultation consultation = consultationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation", id));
        consultationRepository.delete(consultation);
    }

    private void storeFiles(Consultation consultation, List<MultipartFile> files) {
        if (files == null || files.isEmpty() || consultation.getTypeConsultation() == null) {
            return;
        }
        String dir = "consultations/" + consultation.getPatient().getCode()
                + "/" + consultation.getTypeConsultation().getName();
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                String path = fileStorageService.store(file, dir);
                ConsultationFile cf = new ConsultationFile();
                cf.setConsultation(consultation);
                cf.setPath(path);
                consultationFileRepository.save(cf);
            }
        }
    }
}

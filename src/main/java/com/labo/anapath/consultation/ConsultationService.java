package com.labo.anapath.consultation;

import com.labo.anapath.common.dto.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ConsultationService {

    PageResponse<ConsultationResponseDto> findAll(int page, int size, UUID branchId,
                                                   UUID patientId, String status, UUID doctorId);

    ConsultationResponseDto findById(UUID id, UUID branchId);

    ConsultationResponseDto create(ConsultationRequestDto dto, UUID branchId);

    ConsultationResponseDto update(UUID id, ConsultationRequestDto dto, List<MultipartFile> files);

    ConsultationResponseDto updateByDoctor(UUID id, ConsultationDoctorUpdateDto dto, List<MultipartFile> files);

    ConsultationResponseDto updateType(UUID id, UUID typeConsultationId);

    /** Liste les fichiers joints à une consultation. */
    List<ConsultationFileResponseDto> getFiles(UUID id);

    void delete(UUID id);
}

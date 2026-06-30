package com.labo.anapath.consultation;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/consultations")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;

    @GetMapping
    @PreAuthorize("hasAuthority('view-consultations')")
    public ResponseEntity<ApiResponse<PageResponse<ConsultationResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID doctorId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                consultationService.findAll(page, size, principal.getBranchId(), patientId, status, doctorId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-consultations')")
    public ResponseEntity<ApiResponse<ConsultationResponseDto>> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(consultationService.findById(id, principal.getBranchId())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('create-consultations')")
    public ResponseEntity<ApiResponse<ConsultationResponseDto>> create(
            @Valid @RequestBody ConsultationRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Consultation créée",
                        consultationService.create(dto, principal.getBranchId())));
    }

    @PutMapping(value = "/{id}", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    @PreAuthorize("hasAuthority('edit-consultations')")
    public ResponseEntity<ApiResponse<ConsultationResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestPart("data") ConsultationRequestDto dto,
            @RequestPart(value = "typeFile", required = false) List<MultipartFile> files) {
        return ResponseEntity.ok(ApiResponse.success("Consultation mise à jour",
                consultationService.update(id, dto, files)));
    }

    @PutMapping("/{id}/doctor")
    @PreAuthorize("hasAuthority('edit-consultations')")
    public ResponseEntity<ApiResponse<ConsultationResponseDto>> updateByDoctor(
            @PathVariable UUID id,
            @Valid @RequestPart("data") ConsultationDoctorUpdateDto dto,
            @RequestPart(value = "typeFile", required = false) List<MultipartFile> files) {
        return ResponseEntity.ok(ApiResponse.success("Consultation mise à jour par le médecin",
                consultationService.updateByDoctor(id, dto, files)));
    }

    @PatchMapping("/{id}/type")
    @PreAuthorize("hasAuthority('edit-consultations')")
    public ResponseEntity<ApiResponse<ConsultationResponseDto>> updateType(
            @PathVariable UUID id,
            @Valid @RequestBody ConsultationTypeUpdateDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Type mis à jour",
                consultationService.updateType(id, dto.getTypeConsultationId())));
    }

    @GetMapping("/{id}/files")
    @PreAuthorize("hasAuthority('view-consultations')")
    public ResponseEntity<ApiResponse<List<ConsultationFileResponseDto>>> getFiles(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(consultationService.getFiles(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('delete-consultations')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        consultationService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Consultation supprimée", null));
    }
}

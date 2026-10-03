package com.labo.anapath.hr;

import com.labo.anapath.common.storage.FichierStockeRepository;
import com.labo.anapath.common.storage.FichierStocke;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.security.UserPrincipal;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLConnection;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employee-documents")
@RequiredArgsConstructor
@Validated
public class EmployeeDocumentController {

    private final EmployeeDocumentRepository documentRepository;
    private final EmployeeRepository employeeRepository;
    private final FileStorageService fileStorageService;
    private final FichierStockeRepository fichiers;

    @Getter
    @Setter
    static class EmployeeDocumentUpdateDto {
        @NotBlank(message = "Le nom du document est obligatoire")
        private String name;
        private String type;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('edit-employees')")
    @Transactional
    public ResponseEntity<ApiResponse<EmployeeDocumentResponseDto>> upload(
            @RequestParam("employeeId") @NotNull UUID employeeId,
            @RequestParam("name") @NotBlank String name,
            @RequestParam(value = "type", required = false) String type,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        Employee employee = employeeRepository.findByIdAndBranchId(employeeId, principal.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Employé", employeeId));
        String filePath = null;
        Long fileSize = null;
        if (file != null && !file.isEmpty()) {
            filePath = fileStorageService.store(file, "documents");
            fileSize = file.getSize();
        }
        EmployeeDocument doc = new EmployeeDocument();
        doc.setBranchId(principal.getBranchId());
        doc.setEmployee(employee);
        doc.setName(name);
        doc.setType(type);
        doc.setFilePath(filePath);
        doc.setFileSize(fileSize);
        EmployeeDocument saved = documentRepository.save(doc);
        fichiers.rattacher(filePath, FichierStocke.EMPLOYEE_DOCUMENT, saved.getId(), principal.getBranchId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document uploadé", toDto(saved)));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('view-employees')")
    public ResponseEntity<ApiResponse<PageResponse<EmployeeDocumentResponseDto>>> findAll(
            @RequestParam @NotNull UUID employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(
                documentRepository.findByEmployeeId(employeeId,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()))
                        .map(this::toDto))));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-employees')")
    public ResponseEntity<ApiResponse<EmployeeDocumentResponseDto>> findById(
            @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        EmployeeDocument doc = documentRepository.findByIdAndBranchId(id, principal.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        return ResponseEntity.ok(ApiResponse.success(toDto(doc)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-employees')")
    @Transactional
    public ResponseEntity<ApiResponse<EmployeeDocumentResponseDto>> update(
            @PathVariable UUID id,
            @RequestBody @jakarta.validation.Valid EmployeeDocumentUpdateDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        EmployeeDocument doc = documentRepository.findByIdAndBranchId(id, principal.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        doc.setName(dto.getName());
        doc.setType(dto.getType());
        return ResponseEntity.ok(ApiResponse.success("Document mis à jour", toDto(documentRepository.save(doc))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-employees')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        EmployeeDocument doc = documentRepository.findByIdAndBranchId(id, principal.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        documentRepository.delete(doc);
        return ResponseEntity.ok(ApiResponse.success("Document supprimé", null));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("hasAuthority('view-employees')")
    public ResponseEntity<Resource> download(
            @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        EmployeeDocument doc = documentRepository.findByIdAndBranchId(id, principal.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Document", id));
        if (doc.getFilePath() == null) {
            throw new ResourceNotFoundException("Fichier physique introuvable", id);
        }
        Resource resource = fileStorageService.load(doc.getFilePath());
        if (!resource.exists()) {
            throw new ResourceNotFoundException("Fichier physique introuvable", id);
        }
        String contentType = URLConnection.guessContentTypeFromName(doc.getFilePath());
        if (contentType == null) contentType = "application/octet-stream";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + doc.getName() + "\"")
                .body(resource);
    }

    private EmployeeDocumentResponseDto toDto(EmployeeDocument doc) {
        return new EmployeeDocumentResponseDto(
                doc.getId(),
                doc.getEmployee().getId(),
                doc.getName(),
                doc.getType(),
                doc.getFilePath(),
                doc.getFileSize(),
                doc.getBranchId(),
                doc.getCreatedAt());
    }
}

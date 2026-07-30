package com.labo.anapath.hr;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/employees/{employeeId}/timeoffs")
@RequiredArgsConstructor
public class EmployeeTimeoffController {

    private final EmployeeTimeoffService employeeTimeoffService;

    @GetMapping
    @PreAuthorize("hasAuthority('view-employees')")
    public ResponseEntity<ApiResponse<PageResponse<EmployeeTimeoffResponseDto>>> findAll(
            @PathVariable UUID employeeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(employeeTimeoffService.findAll(page, size, employeeId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-employees')")
    public ResponseEntity<ApiResponse<EmployeeTimeoffResponseDto>> findById(
            @PathVariable UUID employeeId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(employeeTimeoffService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('edit-employees')")
    public ResponseEntity<ApiResponse<EmployeeTimeoffResponseDto>> create(
            @PathVariable UUID employeeId,
            @Valid @RequestBody EmployeeTimeoffRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Congé créé", employeeTimeoffService.create(dto, employeeId)));
    }

    /** Modifie dates et motif d'une demande — route Laravel {@code employee-timeoff-update}. */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-employees')")
    public ResponseEntity<ApiResponse<EmployeeTimeoffResponseDto>> update(
            @PathVariable UUID employeeId,
            @PathVariable UUID id,
            @Valid @RequestBody EmployeeTimeoffRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Demande de congé mise à jour",
                employeeTimeoffService.update(id, dto, employeeId)));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('edit-employees')")
    public ResponseEntity<ApiResponse<EmployeeTimeoffResponseDto>> updateStatus(
            @PathVariable UUID employeeId,
            @PathVariable UUID id,
            @Valid @RequestBody TimeoffStatusUpdateDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Statut mis à jour", employeeTimeoffService.updateStatus(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-employees')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID employeeId,
            @PathVariable UUID id) {
        employeeTimeoffService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Congé supprimé", null));
    }
}

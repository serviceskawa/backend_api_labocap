package com.labo.anapath.contract;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
public class ContratController {

    private final ContratService contratService;

    @GetMapping
    @PreAuthorize("hasAuthority('view-contrats')")
    public ResponseEntity<ApiResponse<PageResponse<ContratResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                contratService.findAll(page, size, principal.getBranchId(), status, search, dateFrom, dateTo)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-contrats')")
    public ResponseEntity<ApiResponse<ContratResponseDto>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(contratService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('edit-contrats')")
    public ResponseEntity<ApiResponse<ContratResponseDto>> create(
            @Valid @RequestBody ContratRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Contrat créé", contratService.create(dto, principal.getBranchId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-contrats')")
    public ResponseEntity<ApiResponse<ContratResponseDto>> update(
            @PathVariable UUID id, @Valid @RequestBody ContratRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Contrat mis à jour", contratService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-contrats')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        contratService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Contrat supprimé", null));
    }

    @PostMapping("/{id}/details")
    @PreAuthorize("hasAuthority('edit-contrats')")
    public ResponseEntity<ApiResponse<DetailsContratDto>> addCategoryDetail(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryDetailRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ligne ajoutée", contratService.addCategoryDetail(id, dto)));
    }

    @PostMapping("/{id}/details/test")
    @PreAuthorize("hasAuthority('edit-contrats')")
    public ResponseEntity<ApiResponse<DetailsContratDto>> addTestDetail(
            @PathVariable UUID id,
            @Valid @RequestBody TestDetailRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ligne test ajoutée", contratService.addTestDetail(id, dto)));
    }

    @PutMapping("/{id}/details/{detailId}")
    @PreAuthorize("hasAuthority('edit-contrats')")
    public ResponseEntity<ApiResponse<DetailsContratDto>> updateTestDetail(
            @PathVariable UUID id,
            @PathVariable UUID detailId,
            @Valid @RequestBody TestDetailUpdateDto dto) {
        return ResponseEntity.ok(
                ApiResponse.success("Ligne mise à jour", contratService.updateTestDetail(id, detailId, dto)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('edit-contrats')")
    public ResponseEntity<ApiResponse<ContratResponseDto>> activate(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Contrat activé", contratService.activate(id)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('edit-contrats')")
    public ResponseEntity<ApiResponse<ContratResponseDto>> close(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Contrat clôturé", contratService.close(id)));
    }

    @DeleteMapping("/{id}/details/{detailId}")
    @PreAuthorize("hasAuthority('edit-contrats')")
    public ResponseEntity<ApiResponse<Void>> deleteDetail(
            @PathVariable UUID id, @PathVariable UUID detailId) {
        contratService.deleteDetail(id, detailId);
        return ResponseEntity.ok(ApiResponse.success("Ligne supprimée", null));
    }
}

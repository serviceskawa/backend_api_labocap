package com.labo.anapath.inventory;

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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    @PreAuthorize("hasAuthority('view-suppliers')")
    public ResponseEntity<ApiResponse<PageResponse<SupplierResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                supplierService.findAll(page, size, principal.getBranchId())));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('view-suppliers')")
    public ResponseEntity<ApiResponse<List<SupplierResponseDto>>> search(
            @RequestParam String q,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                supplierService.search(q, principal.getBranchId())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-suppliers')")
    public ResponseEntity<ApiResponse<SupplierResponseDto>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(supplierService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('create-suppliers')")
    public ResponseEntity<ApiResponse<SupplierResponseDto>> create(
            @Valid @RequestBody SupplierRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Fournisseur créé",
                        supplierService.create(dto, principal.getBranchId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-suppliers')")
    public ResponseEntity<ApiResponse<SupplierResponseDto>> update(
            @PathVariable UUID id, @Valid @RequestBody SupplierRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Fournisseur mis à jour",
                supplierService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('delete-suppliers')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        supplierService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Fournisseur supprimé", null));
    }
}

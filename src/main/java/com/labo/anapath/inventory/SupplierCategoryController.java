package com.labo.anapath.inventory;

import com.labo.anapath.common.dto.ApiResponse;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/supplier-categories")
@RequiredArgsConstructor
public class SupplierCategoryController {

    private final SupplierCategoryService supplierCategoryService;

    // `view-suppliers` suffit : le formulaire fournisseur doit pouvoir alimenter
    // son select de catégories sans exiger l'accès à l'écran Catégories.
    @GetMapping
    @PreAuthorize("hasAnyAuthority('view-supplier-categories','view-suppliers')")
    public ResponseEntity<ApiResponse<List<SupplierCategoryResponseDto>>> findAll(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                supplierCategoryService.findAll(principal.getBranchId())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('create-supplier-categories')")
    public ResponseEntity<ApiResponse<SupplierCategoryResponseDto>> create(
            @Valid @RequestBody SupplierCategoryRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Catégorie créée",
                        supplierCategoryService.create(dto, principal.getBranchId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-supplier-categories')")
    public ResponseEntity<ApiResponse<SupplierCategoryResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody SupplierCategoryRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Catégorie mise à jour",
                supplierCategoryService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('delete-supplier-categories')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        supplierCategoryService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Catégorie supprimée", null));
    }
}

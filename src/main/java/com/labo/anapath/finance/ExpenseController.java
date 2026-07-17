package com.labo.anapath.finance;

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
@RequestMapping("/api/v1/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    @PreAuthorize("hasAuthority('view-expenses')")
    public ResponseEntity<ApiResponse<PageResponse<ExpenseResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer paid,
            @RequestParam(required = false) UUID expenseCategorieId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                expenseService.findAll(page, size, principal.getBranchId(), paid, expenseCategorieId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-expence-details')")
    public ResponseEntity<ApiResponse<ExpenseResponseDto>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(expenseService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('create-expenses')")
    public ResponseEntity<ApiResponse<ExpenseResponseDto>> create(
            @Valid @RequestBody ExpenseCreateRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Dépense créée",
                        expenseService.create(dto, principal.getBranchId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('create-expenses')")
    public ResponseEntity<ApiResponse<ExpenseResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody ExpenseRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success(expenseService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('create-expenses')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        expenseService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Dépense supprimée", null));
    }

    @PostMapping("/{id}/details")
    @PreAuthorize("hasAuthority('create-expence-details')")
    public ResponseEntity<ApiResponse<ExpenseResponseDto>> addDetail(
            @PathVariable UUID id,
            @Valid @RequestBody ExpenceDetailRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ligne ajoutée",
                        expenseService.addDetail(id, dto, principal.getBranchId())));
    }

    @DeleteMapping("/{id}/details/{detailId}")
    @PreAuthorize("hasAuthority('delete-expence-details')")
    public ResponseEntity<ApiResponse<Void>> removeDetail(
            @PathVariable UUID id,
            @PathVariable UUID detailId) {
        expenseService.removeDetail(id, detailId);
        return ResponseEntity.ok(ApiResponse.success("Ligne supprimée", null));
    }

    @PatchMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('create-expenses')")
    public ResponseEntity<ApiResponse<ExpenseResponseDto>> pay(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                expenseService.payExpense(id, principal.getBranchId(), principal.getId())));
    }

    @PatchMapping("/{id}/update-stock")
    @PreAuthorize("hasAuthority('create-expenses')")
    public ResponseEntity<ApiResponse<ExpenseResponseDto>> updateStock(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                expenseService.updateStock(id, principal.getBranchId(), principal.getId())));
    }
}

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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/refund-requests")
@RequiredArgsConstructor
public class RefundRequestController {

    private final RefundService refundService;

    @PostMapping
    @PreAuthorize("hasAuthority('create-refund-requests')")
    public ResponseEntity<ApiResponse<RefundRequestResponseDto>> create(
            @Valid @RequestBody RefundRequestCreateDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Demande créée",
                        refundService.create(dto, principal.getBranchId(), principal.getId())));
    }

    // Laravel réserve le changement de statut à `view-process-refund-request`.
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('view-process-refund-request')")
    public ResponseEntity<ApiResponse<RefundRequestStatusResult>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody RefundRequestStatusUpdateDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Statut mis à jour",
                refundService.updateStatus(id, dto, principal.getId())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('view-refund-requests')")
    public ResponseEntity<ApiResponse<PageResponse<RefundRequestResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                refundService.findAll(page, size, principal.getBranchId())));
    }

    /**
     * Nombre de demandes en attente, pour le badge du menu « Remboursements »
     * (équivalent du helper Laravel {@code getnbrRefundRequestPending}).
     *
     * @return objet JSON {@code { "count": N }}
     */
    @GetMapping("/count-pending")
    @PreAuthorize("hasAuthority('view-refund-requests')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> countPending(
            @AuthenticationPrincipal UserPrincipal principal) {
        long count = refundService.countPending(principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success(java.util.Map.of("count", count)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-refund-requests')")
    public ResponseEntity<ApiResponse<RefundRequestResponseDto>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(refundService.findById(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('delete-refund-requests')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        refundService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Demande supprimée", null));
    }
}

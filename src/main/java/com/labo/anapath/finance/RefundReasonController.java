package com.labo.anapath.finance;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
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
@RequestMapping("/api/v1/refund-reasons")
@RequiredArgsConstructor
public class RefundReasonController {

    private final RefundReasonRepository refundReasonRepository;

    @GetMapping
    // `view-refund-requests` suffit : le formulaire de demande doit pouvoir
    // alimenter son select de raisons sans accès à l'écran Paramètres.
    @PreAuthorize("hasAnyAuthority('view-refund-reasons','view-refund-requests')")
    public ResponseEntity<ApiResponse<List<RefundReasonResponseDto>>> findAll(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<RefundReasonResponseDto> list = refundReasonRepository.findAll()
                .stream()
                .filter(r -> principal.getBranchId().equals(r.getBranchId()))
                .map(r -> new RefundReasonResponseDto(r.getId(), r.getLabel(), r.getBranchId()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(list));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('create-refund-reasons')")
    public ResponseEntity<ApiResponse<RefundReasonResponseDto>> create(
            @Valid @RequestBody RefundReasonRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        RefundReason reason = new RefundReason();
        reason.setLabel(dto.getLabel());
        reason.setBranchId(principal.getBranchId());
        RefundReason saved = refundReasonRepository.save(reason);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Motif créé",
                        new RefundReasonResponseDto(saved.getId(), saved.getLabel(), saved.getBranchId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-refund-reasons')")
    public ResponseEntity<ApiResponse<RefundReasonResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestBody RefundReasonRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        RefundReason reason = loadForBranch(id, principal);
        reason.setLabel(dto.getLabel());
        RefundReason saved = refundReasonRepository.save(reason);
        return ResponseEntity.ok(ApiResponse.success("Motif mis à jour",
                new RefundReasonResponseDto(saved.getId(), saved.getLabel(), saved.getBranchId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('delete-refund-reasons')")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        RefundReason reason = loadForBranch(id, principal);
        refundReasonRepository.delete(reason);
        return ResponseEntity.ok(ApiResponse.success("Motif supprimé", null));
    }

    private RefundReason loadForBranch(UUID id, UserPrincipal principal) {
        RefundReason reason = refundReasonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Motif de remboursement", id));
        if (!principal.getBranchId().equals(reason.getBranchId())) {
            throw new ResourceNotFoundException("Motif de remboursement", id);
        }
        return reason;
    }
}

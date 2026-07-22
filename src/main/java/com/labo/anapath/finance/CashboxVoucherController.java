package com.labo.anapath.finance;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.security.UserPrincipal;
import com.labo.anapath.common.storage.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
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
@RequestMapping("/api/v1/cashbox-tickets")
@RequiredArgsConstructor
public class CashboxVoucherController {

    private final CashboxVoucherService voucherService;
    private final FileStorageService fileStorageService;

    @GetMapping
    @PreAuthorize("hasAuthority('view-cashbox-tickets')")
    public ResponseEntity<ApiResponse<PageResponse<CashboxVoucherResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                voucherService.findAll(page, size, principal.getBranchId())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-cashbox-tickets')")
    public ResponseEntity<ApiResponse<CashboxVoucherResponseDto>> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(voucherService.findById(id, principal.getBranchId())));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('create-cashbox-tickets')")
    public ResponseEntity<ApiResponse<CashboxVoucherResponseDto>> create(
            @Valid @RequestBody CashboxVoucherRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Bon de caisse créé",
                        voucherService.create(dto, principal.getBranchId())));
    }

    /**
     * Édite l'en-tête d'un bon de caisse et, optionnellement, remplace la pièce
     * jointe (partie multipart `file`). Un fichier absent conserve l'existant.
     */
    @PutMapping(value = "/{id}", consumes = {
            MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    @PreAuthorize("hasAuthority('edit-cashbox-tickets')")
    public ResponseEntity<ApiResponse<CashboxVoucherResponseDto>> update(
            @PathVariable UUID id,
            @Valid @RequestPart("data") CashboxVoucherRequestDto dto,
            @RequestPart(value = "file", required = false) MultipartFile file) {
        if (file != null && !file.isEmpty()) {
            dto.setTicketFile(fileStorageService.store(file, "bons"));
        }
        return ResponseEntity.ok(ApiResponse.success(voucherService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('delete-cashbox-tickets')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        voucherService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Bon de caisse supprimé", null));
    }

    @PostMapping("/{id}/details")
    @PreAuthorize("hasAuthority('create-cashbox-ticket-details')")
    public ResponseEntity<ApiResponse<CashboxVoucherResponseDto>> addDetail(
            @PathVariable UUID id,
            @Valid @RequestBody CashboxVoucherDetailRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ligne ajoutée",
                        voucherService.addDetail(id, dto, principal.getBranchId())));
    }

    @DeleteMapping("/{id}/details/{detailId}")
    @PreAuthorize("hasAuthority('delete-cashbox-ticket-details')")
    public ResponseEntity<ApiResponse<Void>> removeDetail(
            @PathVariable UUID id,
            @PathVariable UUID detailId) {
        voucherService.removeDetail(id, detailId);
        return ResponseEntity.ok(ApiResponse.success("Ligne supprimée", null));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('edit-cashbox-tickets')")
    public ResponseEntity<ApiResponse<CashboxVoucherResponseDto>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody CashboxVoucherStatusDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                voucherService.updateStatus(id, dto, principal.getBranchId(), principal.getId())));
    }
}

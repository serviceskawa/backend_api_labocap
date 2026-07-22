package com.labo.anapath.finance;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.security.UserPrincipal;
import com.labo.anapath.common.storage.FileStorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bank-deposits")
@RequiredArgsConstructor
public class BankDepositController {

    private final BankService bankService;
    private final FileStorageService fileStorageService;

    /**
     * Enregistre un dépôt bancaire depuis la Caisse de vente. La pièce jointe
     * (scan du reçu de dépôt) est transmise en multipart (partie `file`).
     */
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    @PreAuthorize("hasAuthority('create-banks')")
    public ResponseEntity<ApiResponse<BankDepositResponseDto>> create(
            @Valid @RequestPart("data") BankDepositRequestDto dto,
            @RequestPart(value = "file", required = false) MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (file != null && !file.isEmpty()) {
            dto.setAttachement(fileStorageService.store(file, "depots"));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Dépôt bancaire enregistré",
                        bankService.createDeposit(dto, principal.getBranchId(), principal.getId())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('view-cashbox-adds')")
    public ResponseEntity<ApiResponse<PageResponse<BankDepositResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID bankId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                bankService.findDeposits(page, size, principal.getBranchId(), bankId, date)));
    }
}

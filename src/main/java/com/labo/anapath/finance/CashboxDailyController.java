package com.labo.anapath.finance;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cashbox-dailies")
@RequiredArgsConstructor
public class CashboxDailyController {

    private final CashboxDailyService cashboxDailyService;
    private final CashboxDailyPdfService cashboxDailyPdfService;

    @PostMapping
    @PreAuthorize("hasAuthority('create-cashbox-dailies')")
    public ResponseEntity<ApiResponse<CashboxDailyResponseDto>> openOrUpdate(
            @Valid @RequestBody CashboxDailyOpenDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Session de caisse ouverte",
                        cashboxDailyService.openOrUpdate(dto, principal.getBranchId(), principal.getId())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('view-cashbox-dailies')")
    public ResponseEntity<ApiResponse<PageResponse<CashboxDailyResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                cashboxDailyService.findAll(page, size, principal.getBranchId())));
    }

    /**
     * Encaissements à présenter lors d'une fermeture.
     *
     * <p>{@code sessionId} désigne la session que l'on ferme : le total part de
     * son ouverture. Omis, on retombe sur la dernière session ouverte de la
     * branche — comportement d'origine, qui fausse le calcul dès qu'une session
     * plus ancienne est restée ouverte.</p>
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('view-cashbox-dailies')")
    public ResponseEntity<ApiResponse<CashboxDailySummaryDto>> getDailySummary(
            @RequestParam(required = false) UUID sessionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                cashboxDailyService.getDailySummary(principal.getBranchId(), sessionId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-cashbox-dailies')")
    public ResponseEntity<ApiResponse<CashboxDailyResponseDto>> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(cashboxDailyService.findById(id, principal.getBranchId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-cashbox-dailies')")
    public ResponseEntity<ApiResponse<CashboxDailyResponseDto>> closeCashbox(
            @PathVariable UUID id,
            @RequestBody CashboxDailyCloseDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                cashboxDailyService.closeCashbox(id, dto, principal.getId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('delete-cashbox-dailies')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        cashboxDailyService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Session supprimée", null));
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('view-cashbox-dailies')")
    public ResponseEntity<byte[]> generatePdf(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        byte[] pdf = cashboxDailyPdfService.generatePdf(id, principal.getBranchId());
        String filename = "Cloture-caisse-" + id + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(pdf);
    }
}

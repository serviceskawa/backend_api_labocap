package com.labo.anapath.biology.report;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.security.UserPrincipal;
import com.labo.anapath.report.ReportResponseDto;
import com.labo.anapath.report.ValidationSigneeDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Compte-rendu de biologie : validation biologique, réouverture, conclusion et PDF.
 *
 * <p>Validation, réouverture et conclusion sous {@code validate-biology-reports} ;
 * PDF sous {@code view-reports}. Remise, appel, SMS et signature du récupérateur
 * restent ceux de {@code /reports/{id}/…}, communs aux disciplines. Les routes
 * répondent 404 quand le module Biologie est désactivé ({@code /biology-*}).</p>
 *
 * <p>Base URL : {@code /api/v1/biology-reports}</p>
 */
@RestController
@RequestMapping("/api/v1/biology-reports")
@RequiredArgsConstructor
public class BiologyReportController {

    private final BiologyReportService service;
    private final BiologyPdfService pdfService;

    /** Corps de {@code PUT /{reportId}/conclusion}. */
    public record ConclusionDto(String conclusion) {}

    /**
     * Validation biologique. Même contrat que {@code POST /reports/{id}/validate} :
     * corps facultatif portant la preuve de l'appareil (application mobile) ; le
     * web n'envoie rien.
     */
    @PostMapping("/{reportId}/validate")
    @PreAuthorize("hasAuthority('validate-biology-reports')")
    public ResponseEntity<ApiResponse<ReportResponseDto>> validate(
            @PathVariable UUID reportId,
            @Valid @RequestBody(required = false) ValidationSigneeDto preuve,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Compte-rendu validé",
                service.validate(reportId, principal.getId(), principal.getBranchId(), preuve)));
    }

    /** Réouverture d'un compte-rendu validé, non remis. */
    @PostMapping("/{reportId}/reopen")
    @PreAuthorize("hasAuthority('validate-biology-reports')")
    public ResponseEntity<ApiResponse<ReportResponseDto>> reopen(
            @PathVariable UUID reportId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Compte-rendu rouvert",
                service.reopen(reportId, principal.getId(), principal.getBranchId())));
    }

    /** Conclusion générale, imprimée en fin de compte-rendu. */
    @PutMapping("/{reportId}/conclusion")
    @PreAuthorize("hasAuthority('validate-biology-reports')")
    public ResponseEntity<ApiResponse<ReportResponseDto>> conclusion(
            @PathVariable UUID reportId,
            @RequestBody ConclusionDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Conclusion enregistrée",
                service.updateConclusion(reportId, dto != null ? dto.conclusion() : null,
                        principal.getId(), principal.getBranchId())));
    }

    /**
     * PDF du compte-rendu ; non validé, seulement si {@code bio_print_provisional}
     * le permet (mention « RÉSULTATS PROVISOIRES », sans signature), sinon 422.
     */
    @GetMapping("/{reportId}/pdf")
    @PreAuthorize("hasAuthority('view-reports')")
    public ResponseEntity<byte[]> pdf(
            @PathVariable UUID reportId,
            @AuthenticationPrincipal UserPrincipal principal) {
        byte[] pdf = pdfService.generatePdf(reportId, principal.getId(), principal.getBranchId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"BIO-" + reportId + ".pdf\"")
                .body(pdf);
    }
}

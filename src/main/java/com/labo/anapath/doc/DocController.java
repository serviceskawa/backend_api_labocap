package com.labo.anapath.doc;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/docs")
@RequiredArgsConstructor
@Validated
public class DocController {

    private final DocService docService;

    @GetMapping
    @PreAuthorize("hasAuthority('view-documentation-categories')")
    public ResponseEntity<ApiResponse<PageResponse<DocResponseDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                docService.findAll(page, size, principal.getBranchId())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-documentation-categories')")
    public ResponseEntity<ApiResponse<DocResponseDto>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(docService.findById(id)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('edit-documentation-categories')")
    public ResponseEntity<ApiResponse<DocResponseDto>> create(
            @RequestParam @NotBlank @Size(max = 255) String title,
            @RequestParam(required = false) UUID documentationCategoryId,
            @RequestParam @NotNull MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Document créé",
                        docService.create(title, documentationCategoryId, file,
                                principal.getId(), principal.getBranchId())));
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('edit-documentation-categories')")
    public ResponseEntity<ApiResponse<DocVersionResponseDto>> addVersion(
            @PathVariable UUID id,
            @RequestParam(required = false) @Size(max = 255) String title,
            @RequestParam @NotNull MultipartFile file,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Version ajoutée",
                        docService.addVersion(id, title, file,
                                principal.getId(), principal.getBranchId())));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('view-documentation-categories')")
    public ResponseEntity<ApiResponse<List<DocVersionResponseDto>>> getVersions(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(docService.getVersions(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-documentation-categories')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        docService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Document supprimé", null));
    }

    // ------------------------------------------------------------------
    // GED avancée : partage par rôle, partagés avec moi, récents, corbeille
    // ------------------------------------------------------------------

    @PostMapping("/{id}/share")
    @PreAuthorize("hasAuthority('edit-documentation-categories')")
    public ResponseEntity<ApiResponse<DocResponseDto>> share(
            @PathVariable UUID id,
            @Valid @RequestBody DocShareRequestDto request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Document partagé",
                docService.share(id, request.roleId(), principal.getBranchId())));
    }

    @GetMapping("/shared-with-me")
    @PreAuthorize("hasAuthority('view-documentation-categories')")
    public ResponseEntity<ApiResponse<PageResponse<DocResponseDto>>> sharedWithMe(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                docService.findSharedWithMe(page, size, principal.getId(), principal.getBranchId())));
    }

    @GetMapping("/recent")
    @PreAuthorize("hasAuthority('view-documentation-categories')")
    public ResponseEntity<ApiResponse<List<DocResponseDto>>> recent(
            @RequestParam(defaultValue = "5") int limit,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                docService.findRecent(principal.getBranchId(), limit)));
    }

    @GetMapping("/trash")
    @PreAuthorize("hasAuthority('edit-documentation-categories')")
    public ResponseEntity<ApiResponse<PageResponse<DocResponseDto>>> trash(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                docService.findTrash(page, size, principal.getBranchId())));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('edit-documentation-categories')")
    public ResponseEntity<ApiResponse<DocResponseDto>> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Document restauré", docService.restore(id)));
    }

    @DeleteMapping("/{id}/permanent")
    @PreAuthorize("hasAuthority('edit-documentation-categories')")
    public ResponseEntity<ApiResponse<Void>> permanentDelete(@PathVariable UUID id) {
        docService.permanentDelete(id);
        return ResponseEntity.ok(ApiResponse.success("Document supprimé définitivement", null));
    }
}

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping
    @PreAuthorize("hasAuthority('view-articles')")
    public ResponseEntity<ApiResponse<ArticlePageResponseDto>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                articleService.findAll(page, size, principal.getBranchId())));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('view-articles')")
    public ResponseEntity<ApiResponse<List<ArticleResponseDto>>> search(
            @RequestParam String q,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                articleService.search(q, principal.getBranchId())));
    }

    /**
     * Nombre d'articles ayant atteint le stock minimum, pour le badge du menu
     * « Stocks » (équivalent du helper Laravel {@code getnbrStockMinim}).
     *
     * @param principal principal Spring Security contenant le branchId
     * @return objet JSON {@code { "count": N }}
     */
    @GetMapping("/count-stock-minimum")
    @PreAuthorize("hasAuthority('view-articles')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> countStockMinimum(
            @AuthenticationPrincipal UserPrincipal principal) {
        long count = articleService.countStockMinimumReached(principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success(java.util.Map.of("count", count)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-articles')")
    public ResponseEntity<ApiResponse<ArticleResponseDto>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(articleService.findById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('create-articles')")
    public ResponseEntity<ApiResponse<ArticleResponseDto>> create(
            @Valid @RequestBody ArticleRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Article créé",
                        articleService.create(dto, principal.getBranchId(), principal.getId())));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-articles')")
    public ResponseEntity<ApiResponse<ArticleResponseDto>> update(
            @PathVariable UUID id, @Valid @RequestBody ArticleRequestDto dto) {
        return ResponseEntity.ok(ApiResponse.success("Article mis à jour",
                articleService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('delete-articles')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        articleService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Article supprimé", null));
    }
}

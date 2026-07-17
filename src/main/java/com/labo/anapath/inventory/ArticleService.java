package com.labo.anapath.inventory;

import java.util.List;
import java.util.UUID;

public interface ArticleService {
    ArticlePageResponseDto findAll(int page, int size, UUID branchId);
    ArticleResponseDto findById(UUID id);
    ArticleResponseDto create(ArticleRequestDto dto, UUID branchId, UUID userId);
    List<ArticleResponseDto> search(String q, UUID branchId);
    ArticleResponseDto update(UUID id, ArticleRequestDto dto);
    void delete(UUID id);
    long countStockMinimumReached(UUID branchId);
}

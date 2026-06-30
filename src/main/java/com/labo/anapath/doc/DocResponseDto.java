package com.labo.anapath.doc;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocResponseDto(
        UUID id,
        String title,
        String attachment,
        Boolean isCurrentVersion,
        Long fileSize,
        UUID documentationCategoryId,
        UUID userId,
        UUID roleId,
        UUID branchId,
        LocalDateTime createdAt
) {}

package com.labo.anapath.finance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record CashboxVoucherResponseDto(
        UUID id,
        UUID cashboxId,
        String code,
        BigDecimal amount,
        String description,
        String status,
        UUID supplierId,
        String supplierName,
        UUID expenseCategoryId,
        String ticketFile,
        List<CashboxVoucherDetailResponseDto> details,
        UUID branchId,
        LocalDateTime createdAt
) {}

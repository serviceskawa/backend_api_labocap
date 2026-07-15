package com.labo.anapath.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CashboxOperationResponseDto(
        UUID id,
        UUID cashboxId,
        BigDecimal amount,
        String type,
        String description,
        LocalDate operationDate,
        String invoiceCode,
        String paymentType,
        String userName,
        UUID branchId,
        LocalDateTime createdAt
) {}

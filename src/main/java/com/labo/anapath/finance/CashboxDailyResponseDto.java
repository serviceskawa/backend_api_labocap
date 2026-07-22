package com.labo.anapath.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record CashboxDailyResponseDto(
        UUID id,
        UUID cashboxId,
        BigDecimal openingBalance,
        BigDecimal closingBalance,
        LocalDate date,
        Integer status,
        String code,
        BigDecimal cashCalculated,
        BigDecimal cashConfirmation,
        BigDecimal cashEcart,
        BigDecimal mobileMoneyCalculated,
        BigDecimal moneyMoneyConfirmation,
        BigDecimal mobileMoneyEcart,
        BigDecimal chequeCalculated,
        BigDecimal chequeConfirmation,
        BigDecimal chequeEcart,
        BigDecimal virementCalculated,
        BigDecimal virementConfirmation,
        BigDecimal virementEcart,
        BigDecimal totalCalculated,
        BigDecimal totalConfirmation,
        BigDecimal totalEcart,
        UUID branchId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String userName,
        String description
) {}

package com.labo.anapath.finance;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CashboxResponseDto(UUID id, String name, String type, BigDecimal balance, Integer statut, UUID branchId, LocalDateTime createdAt) {}

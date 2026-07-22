package com.labo.anapath.hr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record EmployeeContratResponseDto(
        UUID id,
        UUID employeeId,
        String type,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal salary,
        LocalDate probationEndDate,
        Integer weeklyWorkHours,
        Integer workingDaysPerWeek,
        String terminationReason,
        BigDecimal hourlyGrossRate,
        BigDecimal transportAllowance,
        String iban,
        String bic,
        LocalDateTime createdAt
) {}

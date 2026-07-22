package com.labo.anapath.hr;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de réponse représentant les informations d'un employé du laboratoire.
 */
public record EmployeeResponseDto(
        UUID id,
        String firstName,
        String lastName,
        String phone,
        String email,
        String position,
        BigDecimal salary,
        LocalDate hireDate,
        String address,
        LocalDate dateOfBirth,
        String placeOfBirth,
        String cnssNumber,
        String photoUrl,
        String gender,
        String nationality,
        String city,
        UUID userId,
        UUID branchId,
        LocalDateTime createdAt
) {}

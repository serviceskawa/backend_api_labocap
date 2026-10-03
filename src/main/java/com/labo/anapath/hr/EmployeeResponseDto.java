package com.labo.anapath.hr;

import com.labo.anapath.common.storage.FichierStocke;
import com.fasterxml.jackson.annotation.JsonProperty;
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
) {
    /** L'identifiant du fichier pour {@code GET /files/{id}}, déduit du chemin. */
    @JsonProperty
    public UUID fileId() {
        return FichierStocke.idPour(photoUrl);
    }
}

package com.labo.anapath.hr;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO de requête pour la création ou la mise à jour d'un employé.
 */
@Getter
@Setter
public class EmployeeRequestDto {

    @NotBlank(message = "Le prénom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    private String phone;
    private String email;
    private String position;
    private BigDecimal salary;
    private LocalDate hireDate;

    // Identité étendue (profil employé Laravel). Tous optionnels : un champ
    // null en update conserve la valeur existante (voir EmployeeServiceImpl).
    private String address;
    private LocalDate dateOfBirth;
    private String placeOfBirth;
    private String cnssNumber;
    private String photoUrl;
    private String gender;
    private String nationality;
    private String city;

    private UUID userId;
}

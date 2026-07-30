package com.labo.anapath.inventory;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
import jakarta.validation.constraints.Pattern;

@Getter
@Setter
public class SupplierRequestDto {

    @NotBlank(message = "Le nom du fournisseur est obligatoire")
    private String name;

    @Pattern(regexp = "^$|^\\+?[0-9][0-9 .-]{6,18}[0-9]$",
             message = "Numéro invalide : 8 à 15 chiffres, indicatif + facultatif (ex. 97000000)")
    private String phone;
    private String email;
    private String address;
    private String information;
    private String category;
    private UUID categoryId;
}

package com.labo.anapath.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class ArticleRequestDto {

    @NotBlank(message = "Le nom de l'article est obligatoire")
    private String name;

    private String code;
    private String description;
    private String unit;
    private BigDecimal purchasePrice;
    @DecimalMin(value = "1", message = "Le seuil d'alerte doit être supérieur ou égal à 1")
    private BigDecimal minimumStock;

    @DecimalMin(value = "1", message = "La quantité en stock doit être supérieure ou égale à 1")
    private BigDecimal initialQuantity;
    private String lotNumber;
    private LocalDate expirationDate;
    private UUID supplierId;
}

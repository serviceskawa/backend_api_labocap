package com.labo.anapath.finance;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class CashboxVoucherDetailRequestDto {

    @NotBlank(message = "La désignation de l'article est requise")
    private String itemName;

    @NotNull(message = "La quantité est requise")
    @DecimalMin(value = "1", message = "La quantité doit être supérieure ou égale à 1")
    @Digits(integer = 12, fraction = 0, message = "La quantité doit être un nombre entier")
    private BigDecimal quantity;

    /**
     * Prix unitaire, en FCFA : devise sans sous-unité, donc un entier d'au moins
     * 1. Un prix décimal serait de toute façon arrondi à l'affichage et fausserait
     * le total du bon.
     */
    @NotNull(message = "Le prix unitaire est requis")
    @DecimalMin(value = "1", message = "Le prix unitaire doit être d'au moins 1 FCFA")
    @Digits(integer = 12, fraction = 0, message = "Le prix unitaire doit être un nombre entier (FCFA)")
    private BigDecimal unitPrice;
}

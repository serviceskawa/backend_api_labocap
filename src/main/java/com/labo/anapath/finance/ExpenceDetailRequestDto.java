package com.labo.anapath.finance;

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
public class ExpenceDetailRequestDto {

    @NotBlank(message = "Le nom de l'article est requis")
    private String articleName;

    @NotNull(message = "La quantité est requise")
    @DecimalMin(value = "1", message = "La quantité doit être supérieure ou égale à 1")
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 0,
            message = "La quantité doit être un nombre entier")
    private BigDecimal quantity;

    /**
     * Prix unitaire, en FCFA : devise sans sous-unité, donc un entier d'au moins 1.
     */
    @NotNull(message = "Le prix unitaire est requis")
    @DecimalMin(value = "1", message = "Le prix unitaire doit être d'au moins 1 FCFA")
    @jakarta.validation.constraints.Digits(integer = 12, fraction = 0,
            message = "Le prix unitaire doit être un nombre entier (FCFA)")
    private BigDecimal unitPrice;
}

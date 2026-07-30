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
    private BigDecimal quantity;

    @NotNull(message = "Le prix unitaire est requis")
    @DecimalMin(value = "0.01", message = "Le prix unitaire doit être supérieur à 0")
    private BigDecimal unitPrice;
}

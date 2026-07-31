package com.labo.anapath.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class ExpenseRequestDto {

    /**
     * Montant de la dépense. Facultatif : il est normalement dérivé de la somme
     * des lignes d'articles (voir {@code addDetail}/{@code removeDetail}), et
     * l'écran de détail ne le transmet plus. S'il est fourni, il écrase la
     * valeur calculée.
     */
    @DecimalMin(value = "0.01", message = "Le montant doit être supérieur à 0")
    private BigDecimal amount;

    @NotNull(message = "La catégorie de dépense est requise")
    private UUID expenseCategorieId;

    private String description;
    private UUID supplierId;
    private String invoiceNumber;
    private LocalDate date;
    private String payment;
    private String receipt;
}

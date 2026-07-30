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

    @NotNull(message = "Le montant est requis")
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

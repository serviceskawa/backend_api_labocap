package com.labo.anapath.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class CashboxOperationCreateDto {

    @NotNull(message = "La caisse est obligatoire")
    private UUID cashboxId;

    @NotNull(message = "Le montant est obligatoire")
    @DecimalMin(value = "0.01", message = "Le montant doit être positif")
    private BigDecimal amount;

    @NotBlank(message = "Le type est obligatoire (CREDIT ou DEBIT)")
    private String type;

    private String description;
    private LocalDate operationDate;
    private String reference;
    private String chequeNumber;
    /** Banque associée à l'approvisionnement (dépôt par chèque / virement). */
    private UUID bankId;
}

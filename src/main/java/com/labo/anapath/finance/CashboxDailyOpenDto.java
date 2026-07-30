package com.labo.anapath.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CashboxDailyOpenDto {

    @NotNull(message = "Le solde d'ouverture est requis")
    @DecimalMin(value = "0", message = "Le solde d'ouverture ne peut pas être négatif")
    private BigDecimal soldeOuverture;

    private UUID cashboxId;
}

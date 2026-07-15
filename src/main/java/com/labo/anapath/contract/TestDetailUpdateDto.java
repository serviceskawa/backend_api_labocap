package com.labo.anapath.contract;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Requête de mise à jour de la remise d'une ligne d'examen d'un contrat
 * (équivalent de l'édition de la réduction dans la vue détail Laravel).
 */
@Getter
@Setter
public class TestDetailUpdateDto {

    @NotNull
    private BigDecimal amountRemise;

    @NotNull
    private BigDecimal amountAfterRemise;
}

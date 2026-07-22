package com.labo.anapath.finance;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class CashboxDailyCloseDto {

    private BigDecimal closingBalance;

    private BigDecimal cashCalculated;
    private BigDecimal cashConfirmation;
    private BigDecimal cashEcart;

    private BigDecimal mobileMoneyCalculated;
    private BigDecimal moneyMoneyConfirmation;
    private BigDecimal mobileMoneyEcart;

    private BigDecimal chequeCalculated;
    private BigDecimal chequeConfirmation;
    private BigDecimal chequeEcart;

    private BigDecimal virementCalculated;
    private BigDecimal virementConfirmation;
    private BigDecimal virementEcart;

    private BigDecimal totalCalculated;
    private BigDecimal totalConfirmation;
    private BigDecimal totalEcart;

    /** Commentaire de clôture (obligatoire côté front en cas d'écart). */
    private String description;
}

package com.labo.anapath.finance;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceDetailDto(
        UUID id,
        UUID labTestId,
        String testName,

        /**
         * Le libellé choisi à la main, s'il y en a un.
         *
         * <p>Rendu à côté de {@code testName} et non à sa place : l'écran doit
         * pouvoir montrer ce qui a été substitué, sinon on ne saurait plus quel
         * acte a réellement été rendu.</p>
         */
        String customTestName,
        Double price,
        Double discount,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal total
) {}

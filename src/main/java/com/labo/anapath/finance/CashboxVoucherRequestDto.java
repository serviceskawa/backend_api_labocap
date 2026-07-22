package com.labo.anapath.finance;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class CashboxVoucherRequestDto {

    // Optionnel (Laravel ne collecte pas d'« objet » sur le bon de caisse).
    private String description;

    private UUID supplierId;
    private UUID expenseCategoryId;
    private UUID cashboxId;
    private String ticketFile;
}

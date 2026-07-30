package com.labo.anapath.contract;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ContratRequestDto {

    private String name;

    private String type;

    private String description;

    private UUID hospitalId;

    private UUID clientId;

    @NotNull(message = "La date de début est obligatoire")
    private LocalDate startDate;

    private LocalDate endDate;

    /**
     * Nombre d'examens couverts par le contrat. La valeur {@code -1} signifie
     * « illimité » (convention Laravel, reprise par l'écran de saisie) ; toute
     * autre valeur doit être supérieure ou égale à 1. Le zéro et les négatifs
     * autres que -1 n'ont pas de sens métier.
     */
    @Min(value = -1, message = "Le nombre d'examens doit être supérieur ou égal à 1, ou -1 pour illimité")
    private int nbrTests;

    private String status = "INACTIF";

    private Boolean invoiceUnique = true;

    private List<ContratDetailRequestDto> details = new ArrayList<>();

    @Getter
    @Setter
    public static class ContratDetailRequestDto {
        private UUID labTestId;
        private BigDecimal price;
    }
}

package com.labo.anapath.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMax;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class MovementRequestDto {

    @NotNull(message = "L'article est obligatoire")
    private UUID articleId;

    @NotNull(message = "Le type de mouvement est obligatoire")
    private MovementType type;

    @NotNull(message = "La quantité est obligatoire")
    @DecimalMin(value = "0.01", message = "La quantité doit être positive")
    @DecimalMin(value = "1", message = "La quantité doit être supérieure ou égale à 1")
    @DecimalMax(value = "1000000", message = "La quantité ne peut pas dépasser 1 000 000")
    private BigDecimal quantity;

    private String notes;

    /** Date du mouvement — par défaut aujourd'hui si non renseignée. */
    private LocalDate movementDate;
}

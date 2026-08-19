package com.labo.anapath.testorder;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class AssignmentDetailRequestDto {
    @NotNull(message = "L'identifiant du bon d'examen est obligatoire")
    private UUID testOrderId;
    /**
     * Étiquettes des prélèvements qui partent — « L1 », « L2 »…
     *
     * <p>Facultatives : une demande à prélèvement unique n'en a pas besoin, et
     * les exiger ferait inventer une étiquette là où il n'y en a pas.</p>
     */
    private List<String> labels;

    private String note;
    private LocalDate date;
}

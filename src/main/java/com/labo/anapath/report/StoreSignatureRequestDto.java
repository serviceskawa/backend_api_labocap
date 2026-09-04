package com.labo.anapath.report;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StoreSignatureRequestDto {

    @NotBlank(message = "Le nom du signataire est obligatoire")
    private String signatorName;

    @NotBlank(message = "La signature est obligatoire")
    private String signature;

    /**
     * Qualité du récupérateur — « Lui-même », « Mère », « Coursier »…
     *
     * <p>Facultative : la rendre obligatoire bloquerait les guichets qui ne
     * l'ont pas encore, et un champ qu'on remplit pour passer outre ne vaut
     * rien comme justification.</p>
     */
    @Size(max = 120, message = "La qualité du récupérateur est trop longue")
    private String relation;
}

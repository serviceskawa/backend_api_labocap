package com.labo.anapath.biology.results;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Valeurs d'une analyse PANEL
 * ({@code PUT /api/v1/biology-results/orders/{testOrderId}/analyses/{labTestId}}).
 *
 * <ul>
 *   <li>un paramètre listé avec une valeur est enregistré (créé ou remplacé) ;</li>
 *   <li>un paramètre listé avec une valeur vide est effacé ;</li>
 *   <li>un paramètre absent de la liste est laissé tel quel.</li>
 * </ul>
 * L'indicateur, l'unité et les valeurs de référence sont calculés par le serveur ;
 * seul un indicateur manuel peut être proposé. Le commentaire est remplacé tel qu'envoyé.
 */
@Getter
@Setter
public class BiologyPanelResultsRequestDto {

    /** Valeurs saisies. */
    @Valid
    private List<Value> values = new ArrayList<>();

    /** Commentaire de l'analyse ; vide ou {@code null} l'efface. */
    @Size(max = 5000, message = "Le commentaire ne doit pas dépasser 5000 caractères")
    private String comment;

    /** Valeur d'un paramètre. */
    @Getter
    @Setter
    public static class Value {

        /** Paramètre de l'analyse. */
        @NotNull(message = "Le paramètre est obligatoire")
        private UUID parameterId;

        /** Valeur saisie (virgule décimale acceptée) ; vide = effacer. */
        @Size(max = 2000, message = "Une valeur ne doit pas dépasser 2000 caractères")
        private String value;

        /**
         * Indicateur manuel, à la place du calcul : N, L, H, LL, HH (chiffré) ou A
         * (texte, choix). Absent : indicateur calculé.
         */
        private String flagOverride;
    }
}

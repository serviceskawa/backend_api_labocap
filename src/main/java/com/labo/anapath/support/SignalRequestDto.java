package com.labo.anapath.support;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class SignalRequestDto {
    /**
     * Identifiant de la demande d'examen. Facultatif : on peut à la place fournir
     * {@link #testOrderCode}, que le serveur résout lui-même.
     */
    private UUID testOrderId;

    /**
     * Code saisi par l'utilisateur (ex. « 26-0008 »). C'est ce que poste le
     * formulaire, et c'est le serveur qui le résout en demande d'examen — comme
     * {@code SignalController::store()} en Laravel
     * ({@code TestOrder::where('code', $code)->first()}). Le front faisait cette
     * résolution lui-même via la recherche, ce qui échouait dès que la recherche
     * ne renvoyait pas le bon exact.
     */
    private String testOrderCode;

    @NotBlank(message = "Le type de signal est requis")
    private String typeSignal;

    @NotBlank(message = "Le commentaire est requis")
    private String commentaire;
}

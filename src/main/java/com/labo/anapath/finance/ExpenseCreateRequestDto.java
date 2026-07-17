package com.labo.anapath.finance;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Création d'une dépense, calquée sur le formulaire en ligne de la liste Laravel
 * (« Catégorie de dépense », « Fournisseur », « Objet »).
 *
 * <p>À la création, le montant n'est pas encore connu : il est saisi ensuite sur
 * la page détail, ou calculé par l'ajout de lignes d'articles. La dépense naît
 * donc avec {@code amount = 0} et {@code paid = 0}. C'est pourquoi ce DTO est
 * distinct de {@link ExpenseRequestDto}, qui sert à la mise à jour et exige un
 * montant valide.
 */
@Getter
@Setter
@NoArgsConstructor
public class ExpenseCreateRequestDto {

    @NotNull
    private UUID expenseCategorieId;

    private String description;

    /** Fournisseur déjà connu, s'il a été choisi dans la liste de suggestions. */
    private UUID supplierId;

    /**
     * Nom saisi librement dans le champ « Fournisseur ». S'il ne correspond à
     * aucun fournisseur existant, il en est créé un (comportement Laravel), la
     * comparaison se faisant sans tenir compte de la casse pour éviter les
     * doublons du type « ACME » / « Acme ».
     */
    private String supplierName;
}

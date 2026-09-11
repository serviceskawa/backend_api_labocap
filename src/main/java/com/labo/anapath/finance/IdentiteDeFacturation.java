package com.labo.anapath.finance;

/**
 * À qui la facture est adressée.
 *
 * <p>Saisie libre, et non choisie dans un référentiel : un laboratoire facture
 * des établissements qu'il ne reverra pas, et leur créer une fiche à chacun
 * encombrerait une table pour un seul examen. Le prix de cette souplesse est
 * qu'une même clinique peut s'écrire de deux façons ; c'est un arbitrage assumé
 * du laboratoire.</p>
 *
 * @param nom     raison sociale de l'établissement, ou nom de la personne
 * @param adresse adresse portée sur la facture et déclarée
 * @param ifu     identifiant fiscal, facultatif — un patient n'en a pas
 */
public record IdentiteDeFacturation(String nom, String adresse, String ifu) {

    /** Vraie quand un nom est donné : sans nom, il n'y a personne à facturer. */
    public boolean estRenseignee() {
        return nom != null && !nom.isBlank();
    }
}

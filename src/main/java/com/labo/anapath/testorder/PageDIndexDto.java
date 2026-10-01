package com.labo.anapath.testorder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Une tranche de l'index des demandes, telle que le mobile la rapatrie.
 *
 * <h2>Pourquoi l'index se découpe</h2>
 *
 * <p>Il tenait en un seul appel. Mesuré sur le jeu de travail, cet appel rend
 * 709 Ko pour 5 906 demandes, et l'application le borne à vingt secondes :
 * il faut donc 284 kbit/s tenus de bout en bout. En EDGE ou en 3G faible —
 * l'ordinaire d'une tournée — le délai expire, et rien n'est gardé : le
 * téléphone a téléchargé six cents kilo-octets qu'il jette, et le nouvel essai
 * repart de zéro. L'index ne descendait donc jamais là où il sert le plus.</p>
 *
 * <p>Par tranches de cinq cents, chaque appel pèse une soixantaine de
 * kilo-octets et passe sur un lien médiocre. Ce qui est arrivé est gardé ; une
 * coupure à la septième tranche en laisse six acquises au lieu de rien.</p>
 *
 * <h2>Ce que porte [jusqua]</h2>
 *
 * <p>L'instant où le jeu a été figé. Le serveur le calcule au premier appel et
 * le rend ; l'application le repasse aux suivants. Sans lui, une demande
 * enregistrée au comptoir pendant le rapatriement décalerait les pages et en
 * ferait manquer une entrée — celle-là même qu'on viendrait scanner.</p>
 *
 * @param contenu       les entrées de cette tranche
 * @param page          son rang, à partir de zéro
 * @param taille        le nombre d'entrées demandé par tranche
 * @param total         le nombre total d'entrées du jeu figé
 * @param derniere      vrai s'il n'y a rien après
 * @param jusqua        l'instant qui fige le jeu, à repasser aux appels suivants
 */
public record PageDIndexDto(
        List<EntreeDIndexDto> contenu,
        int page,
        int taille,
        long total,
        boolean derniere,
        LocalDateTime jusqua) {
}

package com.labo.anapath.finance;

/**
 * Le libellé qu'on veut voir paraître sur une ligne de facture.
 *
 * <p>Vide ou blanc : la ligne revient au nom du catalogue. C'est ainsi qu'on
 * défait une personnalisation, sans avoir besoin d'un second geste.</p>
 */
public record LibelleDeLigneDto(String customTestName) {}

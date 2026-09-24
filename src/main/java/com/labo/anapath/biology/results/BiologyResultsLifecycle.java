package com.labo.anapath.biology.results;

import java.util.Collection;
import java.util.UUID;

/**
 * Tenue des analyses à saisir d'un bon de biologie, appelée par le service des bons.
 *
 * <p>Jamais appelée pour un bon d'anatomie pathologique.</p>
 */
public interface BiologyResultsLifecycle {

    /**
     * Aligne les analyses à saisir d'un bon validé sur ses analyses :
     * <ul>
     *   <li>une ligne {@code PENDING} pour chaque analyse qui n'en a pas ;</li>
     *   <li>suppression (logique) de la ligne d'une analyse retirée du bon — seulement
     *       si elle ne porte aucun résultat, ce que {@code BiologyResultsGuard} a
     *       vérifié avant ;</li>
     *   <li>recalcul de l'état du compte-rendu (une analyse ajoutée à un bon prêt le
     *       renvoie en saisie).</li>
     * </ul>
     * Rejouable : un second appel avec les mêmes analyses ne change rien.
     *
     * @param testOrderId bon de biologie validé
     * @param branchId    succursale
     * @param labTestIds  analyses du bon (doublons et {@code null} ignorés)
     * @param userId      auteur, pour le journal ; {@code null} = utilisateur connecté
     */
    void aligner(UUID testOrderId, UUID branchId, Collection<UUID> labTestIds, UUID userId);
}

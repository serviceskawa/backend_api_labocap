package com.labo.anapath.biology;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Point d'extension consulté par la modification d'un bon de biologie : une analyse
 * qui porte déjà des résultats ne se retire pas du bon.
 *
 * <p>Les résultats sont rattachés au couple (bon, analyse) — {@code test_order_id},
 * {@code lab_test_id} — et non à la ligne {@code detail_test_orders}, que la
 * modification du bon supprime et recrée à chaque enregistrement. C'est donc sur
 * ce couple que porte la question.</p>
 *
 * <p>Implémentée par {@link com.labo.anapath.biology.results.ResultatsDuBon}, qui lit
 * les tables de résultats : une analyse porte des résultats si elle n'est plus en
 * attente de saisie ou si une valeur lui est rattachée.</p>
 *
 * <p>Jamais consulté pour un bon d'anatomie pathologique.</p>
 */
public interface BiologyResultsGuard {

    /**
     * @param testOrderId identifiant du bon de biologie
     * @param labTestIds  analyses que la modification retire du bon (jamais vide)
     * @return celles, parmi {@code labTestIds}, qui portent au moins un résultat saisi ;
     *         vide si toutes peuvent être retirées
     */
    Set<UUID> analysesAvecResultats(UUID testOrderId, Collection<UUID> labTestIds);
}

package com.labo.anapath.biology;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Implémentation de {@link BiologyResultsGuard} tant qu'aucune table de résultats
 * n'existe : aucune analyse ne peut porter de résultat, toutes se retirent.
 *
 * <p>Elle est remplacée — et non complétée — par celle qui lit les résultats
 * saisis : deux implémentations côte à côte rendraient l'injection ambiguë, et
 * c'est voulu, pour qu'aucune des deux ne reste active par oubli.</p>
 */
@Component
class SansResultatsDeBiologie implements BiologyResultsGuard {

    @Override
    public Set<UUID> analysesAvecResultats(UUID testOrderId, Collection<UUID> labTestIds) {
        return Set.of();
    }
}

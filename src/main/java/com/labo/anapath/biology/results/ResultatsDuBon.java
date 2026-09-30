package com.labo.anapath.biology.results;

import com.labo.anapath.biology.BiologyResultsGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Lien entre un bon de biologie et ses résultats : ce que le service des bons
 * demande (peut-on retirer une analyse ?) et ce qu'il déclenche (création des
 * analyses à saisir à la validation, alignement à la modification).
 *
 * <p>Une analyse <b>porte des résultats</b> si son état n'est plus {@code PENDING}
 * ou si une valeur de paramètre, une valeur d'option de culture ou un germe lui est
 * rattaché.</p>
 *
 * <p>Remplace {@code SansResultatsDeBiologie}, l'implémentation vide de B4.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResultatsDuBon implements BiologyResultsGuard, BiologyResultsLifecycle {

    private final BiologyAnalysisResultRepository analysisResultRepository;
    private final AvancementDuCompteRendu avancement;

    /** {@inheritDoc} */
    @Override
    public Set<UUID> analysesAvecResultats(UUID testOrderId, Collection<UUID> labTestIds) {
        if (testOrderId == null || labTestIds == null || labTestIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(analysisResultRepository.findLabTestIdsWithResults(testOrderId, labTestIds));
    }

    /** {@inheritDoc} */
    @Override
    public void aligner(UUID testOrderId, UUID branchId, Collection<UUID> labTestIds, UUID userId) {
        Set<UUID> voulues = labTestIds == null ? Set.of()
                : labTestIds.stream().filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, BiologyAnalysisResult> existantes = analysisResultRepository.findByTestOrderId(testOrderId).stream()
                .collect(Collectors.toMap(BiologyAnalysisResult::getLabTestId, Function.identity(), (a, b) -> a));

        for (UUID labTestId : voulues) {
            if (!existantes.containsKey(labTestId)) {
                BiologyAnalysisResult ligne = new BiologyAnalysisResult();
                ligne.setBranchId(branchId);
                ligne.setTestOrderId(testOrderId);
                ligne.setLabTestId(labTestId);
                ligne.setStatus(BiologyAnalysisStatus.PENDING);
                analysisResultRepository.save(ligne);
            }
        }

        List<UUID> retirees = existantes.keySet().stream().filter(id -> !voulues.contains(id)).toList();
        if (!retirees.isEmpty()) {
            // Deuxième ligne de défense : le service des bons a déjà refusé le retrait
            // d'une analyse saisie ; on ne supprime de toute façon que les lignes vierges.
            Set<UUID> protegees = analysesAvecResultats(testOrderId, retirees);
            for (UUID id : retirees) {
                if (protegees.contains(id)) {
                    log.warn("Analyse {} retirée du bon {} alors qu'elle porte des résultats : ligne conservée.",
                            id, testOrderId);
                } else {
                    analysisResultRepository.delete(existantes.get(id));
                }
            }
        }
        avancement.recalculer(testOrderId, branchId, userId);
    }
}

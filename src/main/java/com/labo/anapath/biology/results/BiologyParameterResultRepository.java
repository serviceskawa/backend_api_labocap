package com.labo.anapath.biology.results;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repository JPA pour {@link BiologyParameterResult} (lignes actives seulement).
 */
@Repository
public interface BiologyParameterResultRepository extends JpaRepository<BiologyParameterResult, UUID> {

    /** Valeurs de plusieurs analyses d'un bon, en une requête. */
    List<BiologyParameterResult> findByAnalysisResult_IdIn(Collection<UUID> analysisResultIds);

    /** Valeurs d'une analyse. */
    List<BiologyParameterResult> findByAnalysisResult_Id(UUID analysisResultId);
}

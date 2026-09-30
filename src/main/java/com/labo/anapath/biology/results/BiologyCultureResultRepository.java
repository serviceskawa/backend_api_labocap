package com.labo.anapath.biology.results;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repository JPA pour {@link BiologyCultureResult} (lignes actives seulement).
 */
@Repository
public interface BiologyCultureResultRepository extends JpaRepository<BiologyCultureResult, UUID> {

    /** Valeurs de plusieurs analyses d'un bon, en une requête. */
    List<BiologyCultureResult> findByAnalysisResult_IdIn(Collection<UUID> analysisResultIds);

    /** Valeurs d'une analyse. */
    List<BiologyCultureResult> findByAnalysisResult_Id(UUID analysisResultId);
}

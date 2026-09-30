package com.labo.anapath.biology.results;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repository JPA pour {@link BiologyIsolate} (lignes actives seulement).
 */
@Repository
public interface BiologyIsolateRepository extends JpaRepository<BiologyIsolate, UUID> {

    /** Germes de plusieurs analyses, dans l'ordre d'affichage. */
    List<BiologyIsolate> findByAnalysisResult_IdInOrderByPositionAsc(Collection<UUID> analysisResultIds);

    /** Germes d'une analyse, dans l'ordre d'affichage. */
    List<BiologyIsolate> findByAnalysisResult_IdOrderByPositionAsc(UUID analysisResultId);
}

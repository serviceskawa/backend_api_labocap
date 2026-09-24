package com.labo.anapath.biology.results;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repository JPA pour {@link BiologyAntibiogramResult} (lignes actives seulement).
 */
@Repository
public interface BiologyAntibiogramResultRepository extends JpaRepository<BiologyAntibiogramResult, UUID> {

    /** Antibiogrammes de plusieurs germes, en une requête. */
    List<BiologyAntibiogramResult> findByIsolate_IdIn(Collection<UUID> isolateIds);
}

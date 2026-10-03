package com.labo.anapath.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.UUID;

public interface JournalAccesRepository extends JpaRepository<JournalAcces, UUID>, JpaSpecificationExecutor<JournalAcces> {
    /**
     * Purge de rétention par la fonction SQL purger_journaux (V103, SECURITY
     * DEFINER) : le rôle applicatif n'a plus le droit de supprimer lui-même.
     */
    @org.springframework.data.jpa.repository.Query(value = "SELECT acces, actions FROM purger_journaux(:avant)", nativeQuery = true)
    Object[] purger(@org.springframework.data.repository.query.Param("avant") LocalDateTime avant);
}

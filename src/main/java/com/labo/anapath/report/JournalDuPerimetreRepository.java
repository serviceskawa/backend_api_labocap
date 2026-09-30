package com.labo.anapath.report;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JournalDuPerimetreRepository extends JpaRepository<JournalDuPerimetre, UUID> {

    /** L'historique d'un compte, du plus récent au plus ancien. */
    List<JournalDuPerimetre> findByUserIdOrderByCreatedAtDesc(UUID userId);
}

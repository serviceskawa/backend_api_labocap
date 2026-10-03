package com.labo.anapath.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Versions successives d'un compte-rendu signé — lecture et ajout seulement. */
@Repository
public interface ReportVersionRepository extends JpaRepository<ReportVersion, UUID> {

    List<ReportVersion> findByReportIdOrderByVersionAsc(UUID reportId);

    Optional<ReportVersion> findByReportIdAndVersion(UUID reportId, int version);

    /** Sert à numéroter : la table n'étant jamais purgée, le compte vaut le dernier numéro. */
    long countByReportId(UUID reportId);
}

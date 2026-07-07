package com.labo.anapath.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository Spring Data JPA pour l'entité {@link LogReport}.
 *
 * <p>Fournit l'accès en lecture au journal de traçabilité des comptes-rendus.
 * Les entrées ne sont jamais modifiées ni supprimées (journal immuable).
 */
@Repository
public interface LogReportRepository extends JpaRepository<LogReport, UUID> {

    /**
     * Retourne l'historique complet des actions effectuées sur un compte-rendu,
     * triées de la plus récente à la plus ancienne.
     *
     * @param reportId identifiant UUID du compte-rendu
     * @return liste d'entrées de journal ordonnée par date décroissante
     */
    List<LogReport> findByReportIdOrderByCreatedAtDesc(UUID reportId);

    /**
     * Retourne l'historique global paginé d'une branche (la plus récente d'abord).
     *
     * @param branchId identifiant de la branche
     * @param pageable pagination + tri
     * @return page d'entrées de journal
     */
    Page<LogReport> findByBranchId(UUID branchId, Pageable pageable);
}

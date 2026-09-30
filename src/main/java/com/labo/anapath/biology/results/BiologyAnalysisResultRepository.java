package com.labo.anapath.biology.results;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA pour {@link BiologyAnalysisResult}.
 *
 * <p>Les requêtes dérivées sont filtrées par {@code deleted_at IS NULL} grâce à la
 * restriction Hibernate de l'entité ; les requêtes natives le font explicitement.</p>
 */
@Repository
public interface BiologyAnalysisResultRepository extends JpaRepository<BiologyAnalysisResult, UUID> {

    /** Analyses actives d'un bon. */
    List<BiologyAnalysisResult> findByTestOrderId(UUID testOrderId);

    /** Analyse active d'un bon. */
    Optional<BiologyAnalysisResult> findByTestOrderIdAndLabTestId(UUID testOrderId, UUID labTestId);

    /**
     * Analyses, parmi {@code labTestIds}, qui portent un résultat : état autre que
     * PENDING, ou au moins une valeur de paramètre, d'option de culture ou un germe.
     *
     * @param testOrderId identifiant du bon
     * @param labTestIds  analyses examinées
     * @return identifiants des analyses (catalogue) qui portent un résultat
     */
    @Query("""
            SELECT r.labTestId FROM BiologyAnalysisResult r
            WHERE r.testOrderId = :testOrderId
              AND r.labTestId IN :labTestIds
              AND (r.status <> com.labo.anapath.biology.results.BiologyAnalysisStatus.PENDING
                   OR EXISTS (SELECT 1 FROM BiologyParameterResult p WHERE p.analysisResult = r)
                   OR EXISTS (SELECT 1 FROM BiologyCultureResult c WHERE c.analysisResult = r)
                   OR EXISTS (SELECT 1 FROM BiologyIsolate i WHERE i.analysisResult = r))
            """)
    List<UUID> findLabTestIdsWithResults(@Param("testOrderId") UUID testOrderId,
                                         @Param("labTestIds") Collection<UUID> labTestIds);

    /**
     * Liste de travail : les analyses des bons de biologie de la succursale.
     *
     * <p>Requête native : les jointures sur l'analyse et le patient ne doivent pas
     * écarter une ligne dont l'analyse aurait été supprimée du catalogue depuis
     * (la restriction Hibernate le ferait). Les urgences d'abord, puis les bons
     * les plus anciens — l'ordre de la paillasse.</p>
     *
     * @param branchId   succursale
     * @param status     état recherché, ou {@code null} pour tous
     * @param categoryId catégorie d'analyse, ou {@code null} pour toutes
     * @param from       début (inclus) sur la date de création du bon
     * @param to         fin (exclue) sur la date de création du bon
     * @param pageable   pagination (sans tri : l'ordre est fixé par la requête)
     */
    @Query(value = """
            SELECT r.id                AS id,
                   r.status            AS status,
                   r.test_order_id     AS testOrderId,
                   o.code              AS orderCode,
                   o.is_urgent         AS urgent,
                   o.created_at        AS orderCreatedAt,
                   o.prelevement_date  AS prelevementDate,
                   p.id                AS patientId,
                   p.code              AS patientCode,
                   p.firstname         AS patientFirstname,
                   p.lastname          AS patientLastname,
                   r.lab_test_id       AS labTestId,
                   l.name              AS labTestName,
                   l.code              AS labTestCode,
                   l.biology_kind      AS biologyKind,
                   c.id                AS categoryId,
                   c.name              AS categoryName,
                   r.entered_at        AS enteredAt,
                   r.tech_validated_at AS techValidatedAt,
                   rp.id               AS reportId,
                   rp.status           AS reportStatus
            FROM biology_analysis_results r
            JOIN test_orders o          ON o.id = r.test_order_id AND o.deleted_at IS NULL
            LEFT JOIN patients p        ON p.id = o.patient_id
            LEFT JOIN lab_tests l       ON l.id = r.lab_test_id
            LEFT JOIN category_tests c  ON c.id = l.category_test_id
            LEFT JOIN reports rp        ON rp.test_order_id = o.id AND rp.deleted_at IS NULL
            WHERE r.deleted_at IS NULL
              AND r.branch_id = :branchId
              AND o.discipline = 'BIOLOGY'
              AND (CAST(:status AS varchar) IS NULL OR r.status = CAST(:status AS varchar))
              AND (CAST(:categoryId AS uuid) IS NULL OR l.category_test_id = CAST(:categoryId AS uuid))
              AND o.created_at >= :from
              AND o.created_at <  :to
            ORDER BY COALESCE(o.is_urgent, FALSE) DESC, o.created_at ASC, o.code ASC, l.name ASC
            """,
            countQuery = """
            SELECT COUNT(*)
            FROM biology_analysis_results r
            JOIN test_orders o    ON o.id = r.test_order_id AND o.deleted_at IS NULL
            LEFT JOIN lab_tests l ON l.id = r.lab_test_id
            WHERE r.deleted_at IS NULL
              AND r.branch_id = :branchId
              AND o.discipline = 'BIOLOGY'
              AND (CAST(:status AS varchar) IS NULL OR r.status = CAST(:status AS varchar))
              AND (CAST(:categoryId AS uuid) IS NULL OR l.category_test_id = CAST(:categoryId AS uuid))
              AND o.created_at >= :from
              AND o.created_at <  :to
            """,
            nativeQuery = true)
    Page<BiologyWorklistProjection> findWorklist(@Param("branchId") UUID branchId,
                                                 @Param("status") String status,
                                                 @Param("categoryId") UUID categoryId,
                                                 @Param("from") LocalDateTime from,
                                                 @Param("to") LocalDateTime to,
                                                 Pageable pageable);
}

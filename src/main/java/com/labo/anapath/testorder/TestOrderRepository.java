package com.labo.anapath.testorder;

import com.labo.anapath.dashboard.DashboardDto;
import com.labo.anapath.dashboard.DashboardProjection;
import com.labo.anapath.patient.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository Spring Data JPA pour l'entité {@link TestOrder}.
 *
 * <p>Étend {@link JpaSpecificationExecutor} pour permettre les requêtes dynamiques
 * via {@link TestOrderSpecification}.
 */
@Repository
public interface TestOrderRepository extends JpaRepository<TestOrder, UUID>, JpaSpecificationExecutor<TestOrder> {

    /**
     * Vérifie si un bon d'examen existe pour le patient donné.
     * Utilisé pour contrôler l'existence d'antécédents avant suppression du patient.
     *
     * @param patient le patient à vérifier
     * @return {@code true} si au moins un bon existe pour ce patient
     */
    boolean existsByPatient(Patient patient);

    /**
     * Récupère un bon d'examen par son identifiant et sa branche.
     * Assure l'isolation multi-tenant : un bon ne peut être accédé que par sa branche propriétaire.
     *
     * @param id       identifiant UUID du bon
     * @param branchId identifiant de la branche
     * @return le bon s'il appartient à la branche, sinon {@link java.util.Optional#empty()}
     */
    Optional<TestOrder> findByIdAndBranchId(UUID id, UUID branchId);

    /**
     * Retourne tous les bons d'examen d'un patient, triés du plus récent au plus ancien.
     *
     * @param patient le patient concerné
     * @return liste ordonnée par date de création décroissante
     */
    List<TestOrder> findByPatientOrderByCreatedAtDesc(Patient patient);

    /**
     * Récupère les bons d'examen d'une branche ayant un code généré pour une année donnée,
     * triés par code décroissant. Utilisé par l'algorithme de génération de code
     * pour déterminer le dernier numéro de séquence de l'année.
     *
     * @param branchId identifiant de la branche
     * @param year     année civile (ex. 2026)
     * @param pageable pagination (en pratique : page 0, taille 1 pour obtenir le dernier)
     * @return liste des bons correspondants
     */
    // -------------------------------------------------------------------------
    // Dashboard — comptages globaux
    // -------------------------------------------------------------------------

    long countByBranchId(UUID branchId);

    /** Nombre de bons d'examen consommés par un contrat (équivalent Laravel {@code $contrat->orders->count()}). */
    long countByContratId(UUID contratId);

    @Query("SELECT COUNT(t) FROM TestOrder t WHERE t.branchId = :branchId AND t.createdAt >= :start AND t.createdAt <= :end")
    long countByBranchIdAndCreatedAtBetween(@Param("branchId") UUID branchId,
                                             @Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    @Query(value = """
            SELECT COUNT(*) FROM test_orders t
            WHERE t.branch_id = :branchId AND t.deleted_at IS NULL
              AND NOT EXISTS (SELECT 1 FROM reports r WHERE r.test_order_id = t.id AND r.deleted_at IS NULL)
            """, nativeQuery = true)
    long countByBranchIdAndReportIsNull(@Param("branchId") UUID branchId);

    @Query("SELECT COUNT(t) FROM TestOrder t WHERE t.branchId = :branchId AND t.status = com.labo.anapath.testorder.TestOrderStatus.PENDING AND t.createdAt < :before")
    long countByBranchIdAndStatusPendingAndCreatedAtBefore(@Param("branchId") UUID branchId,
                                                            @Param("before") LocalDateTime before);

    /**
     * Demandes d'examen sans macro depuis plus de {@code days} jours (alerte
     * "macro non faite"). Réplique la règle Laravel : test_orders absents de
     * test_pathology_macros et créés il y a plus de N jours.
     */
    @Query(value = """
            SELECT t.* FROM test_orders t
            WHERE t.branch_id = :branchId
              AND t.deleted_at IS NULL
              AND DATE(t.created_at) <= CURRENT_DATE - :days
              AND NOT EXISTS (
                  SELECT 1 FROM test_pathology_macros m
                  WHERE m.test_order_id = t.id AND m.deleted_at IS NULL)
            ORDER BY t.created_at ASC
            LIMIT :maxRows
            """, nativeQuery = true)
    List<TestOrder> findOverdueWithoutMacro(@Param("branchId") UUID branchId,
                                            @Param("days") int days,
                                            @Param("maxRows") int maxRows);

    /**
     * Demandes d'examen dont le compte-rendu n'est toujours pas validé depuis plus de
     * {@code days} jours (alerte "compte-rendu non fait"). Réplique la règle Laravel :
     * {@code test_orders} créés il y a plus de N jours dont le rapport associé n'est pas
     * terminé (statut différent de VALIDATED/DELIVERED). Le destinataire est le
     * pathologiste assigné ({@code attribuate_doctor_id}).
     */
    @Query(value = """
            SELECT t.code AS testOrderCode,
                   u.email AS email,
                   CONCAT(u.firstname, ' ', u.lastname) AS doctorName
            FROM test_orders t
            JOIN reports r ON r.test_order_id = t.id AND r.deleted_at IS NULL
            JOIN users u ON u.id = t.attribuate_doctor_id AND u.deleted_at IS NULL
            WHERE t.branch_id = :branchId
              AND t.deleted_at IS NULL
              AND r.status NOT IN ('VALIDATED', 'DELIVERED')
              AND DATE(t.created_at) <= CURRENT_DATE - :days
            ORDER BY t.created_at ASC
            LIMIT :maxRows
            """, nativeQuery = true)
    List<ReportNonFaitProjection> findOverdueWithoutReport(@Param("branchId") UUID branchId,
                                                           @Param("days") int days,
                                                           @Param("maxRows") int maxRows);

    // -------------------------------------------------------------------------
    // Dashboard — top examens
    // -------------------------------------------------------------------------

    @Query(value = """
            SELECT dto.test_name as testName, COUNT(*) as totalDemandes
            FROM detail_test_orders dto
            JOIN test_orders t ON dto.test_order_id = t.id
            WHERE t.branch_id = :branchId AND t.deleted_at IS NULL
            GROUP BY dto.test_name
            ORDER BY totalDemandes DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<DashboardProjection.TopExamen> getTopExamensByBranchId(@Param("branchId") UUID branchId,
                                                          @Param("limit") int limit);

    // -------------------------------------------------------------------------
    // Dashboard — stats docteurs
    // -------------------------------------------------------------------------

    @Query(value = """
            SELECT u.id::text as id, CONCAT(u.lastname, ' ', u.firstname) as doctor,
                   COUNT(DISTINCT t.id) as assigne,
                   COUNT(DISTINCT CASE WHEN r.status = 'VALIDATED' THEN t.id END) as traite
            FROM test_orders t
            JOIN users u ON u.id = t.attribuate_doctor_id
            LEFT JOIN reports r ON r.test_order_id = t.id
            WHERE t.branch_id = :branchId AND t.deleted_at IS NULL AND u.deleted_at IS NULL
            GROUP BY u.id, u.lastname, u.firstname
            ORDER BY assigne DESC
            """, nativeQuery = true)
    List<DashboardProjection.DoctorStat> getDoctorStatsByBranchId(@Param("branchId") UUID branchId);

    // -------------------------------------------------------------------------
    // Dashboard — stats mensuelles
    // -------------------------------------------------------------------------

    @Query(value = """
            SELECT COUNT(DISTINCT dto.id)
            FROM test_orders t
            JOIN detail_test_orders dto ON dto.test_order_id = t.id
            WHERE t.branch_id = :branchId AND EXTRACT(MONTH FROM t.created_at) = :month
              AND EXTRACT(YEAR FROM t.created_at) = :year AND t.deleted_at IS NULL
            """, nativeQuery = true)
    long countByBranchIdAndMonth(@Param("branchId") UUID branchId,
                                  @Param("month") int month,
                                  @Param("year") int year);

    @Query(value = """
            SELECT COALESCE(SUM(lt.price), 0)
            FROM test_orders t
            JOIN detail_test_orders dto ON dto.test_order_id = t.id
            JOIN lab_tests lt ON dto.lab_test_id = lt.id
            WHERE t.branch_id = :branchId AND EXTRACT(MONTH FROM t.created_at) = :month
              AND EXTRACT(YEAR FROM t.created_at) = :year AND t.deleted_at IS NULL
            """, nativeQuery = true)
    BigDecimal sumPriceByBranchIdAndMonth(@Param("branchId") UUID branchId,
                                           @Param("month") int month,
                                           @Param("year") int year);

    @Query(value = """
            SELECT COUNT(DISTINCT t.patient_id)
            FROM test_orders t
            WHERE t.branch_id = :branchId AND EXTRACT(MONTH FROM t.created_at) = :month
              AND EXTRACT(YEAR FROM t.created_at) = :year AND t.deleted_at IS NULL
            """, nativeQuery = true)
    long countPatientsByBranchIdAndMonth(@Param("branchId") UUID branchId,
                                          @Param("month") int month,
                                          @Param("year") int year);

    @Query(value = """
            SELECT h.name as nom, COUNT(DISTINCT t.patient_id) as totalPatients
            FROM test_orders t JOIN hospitals h ON t.hospital_id = h.id
            WHERE t.branch_id = :branchId AND EXTRACT(MONTH FROM t.created_at) = :month
              AND EXTRACT(YEAR FROM t.created_at) = :year AND t.deleted_at IS NULL AND h.deleted_at IS NULL
            GROUP BY h.id, h.name ORDER BY totalPatients DESC
            """, nativeQuery = true)
    List<DashboardProjection.ByItem> countByHospitalAndMonth(@Param("branchId") UUID branchId,
                                                       @Param("month") int month,
                                                       @Param("year") int year);

    @Query(value = """
            SELECT d.name as nom, COUNT(DISTINCT t.patient_id) as totalPatients
            FROM test_orders t JOIN doctors d ON t.doctor_id = d.id
            WHERE t.branch_id = :branchId AND EXTRACT(MONTH FROM t.created_at) = :month
              AND EXTRACT(YEAR FROM t.created_at) = :year AND t.deleted_at IS NULL AND d.deleted_at IS NULL
            GROUP BY d.id, d.name HAVING COUNT(DISTINCT t.patient_id) > 5
            ORDER BY totalPatients DESC
            """, nativeQuery = true)
    List<DashboardProjection.ByItem> countByDoctorAndMonth(@Param("branchId") UUID branchId,
                                                     @Param("month") int month,
                                                     @Param("year") int year);

    @Query(value = """
            SELECT tp.title as nom, COUNT(DISTINCT t.patient_id) as totalPatients
            FROM test_orders t JOIN type_orders tp ON t.type_order_id = tp.id
            WHERE t.branch_id = :branchId AND EXTRACT(MONTH FROM t.created_at) = :month
              AND EXTRACT(YEAR FROM t.created_at) = :year AND t.deleted_at IS NULL
            GROUP BY tp.id, tp.title ORDER BY totalPatients DESC
            """, nativeQuery = true)
    List<DashboardProjection.ByItem> countByTypeOrderAndMonth(@Param("branchId") UUID branchId,
                                                        @Param("month") int month,
                                                        @Param("year") int year);

    // -------------------------------------------------------------------------
    // Dashboard pathologiste — bons affectés via attribuate_doctor_id
    // -------------------------------------------------------------------------

    @Query(value = """
            SELECT t.id::text as id, t.code as code, t.created_at::text as createdAt,
                   p.firstname as patientFirstname, p.lastname as patientLastname,
                   CASE WHEN r.status IN ('VALIDATED','DELIVERED') THEN 1 ELSE 0 END as reportStatus
            FROM test_orders t
            JOIN patients p ON t.patient_id = p.id
            LEFT JOIN reports r ON r.test_order_id = t.id AND r.deleted_at IS NULL
            WHERE t.attribuate_doctor_id = :userId
              AND t.branch_id = :branchId AND t.deleted_at IS NULL
            ORDER BY t.created_at DESC
            """, nativeQuery = true)
    List<DashboardProjection.DoctorOrder> findAllByAttribuateDoctorId(
            @Param("userId") UUID userId,
            @Param("branchId") UUID branchId);

    @Query(value = """
            SELECT t.id::text as id, t.code as code, t.created_at::text as createdAt,
                   p.firstname as patientFirstname, p.lastname as patientLastname,
                   CASE WHEN r.status IN ('VALIDATED','DELIVERED') THEN 1 ELSE 0 END as reportStatus
            FROM test_orders t
            JOIN patients p ON t.patient_id = p.id
            LEFT JOIN reports r ON r.test_order_id = t.id AND r.deleted_at IS NULL
            WHERE t.attribuate_doctor_id = :userId
              AND t.branch_id = :branchId AND t.deleted_at IS NULL
              AND DATE(r.updated_at) = :today
            ORDER BY t.created_at DESC
            """, nativeQuery = true)
    List<DashboardProjection.DoctorOrder> findTodayByAttribuateDoctorId(
            @Param("userId") UUID userId,
            @Param("branchId") UUID branchId,
            @Param("today") java.time.LocalDate today);

    @Query(value = """
            SELECT t.* FROM test_orders t
            WHERE t.deleted_at IS NULL AND t.branch_id = :branchId
              AND t.code IS NOT NULL
              AND EXTRACT(YEAR FROM t.created_at) = :year
            ORDER BY t.code DESC
            LIMIT :#{#pageable.pageSize} OFFSET :#{#pageable.offset}
            """, nativeQuery = true)
    List<TestOrder> findByBranchIdAndCodeNotNullAndYear(
            @Param("branchId") UUID branchId,
            @Param("year") int year,
            Pageable pageable);

    /**
     * Renvoie la plus grande séquence numérique parmi les codes de la branche
     * dont l'année (les 2 chiffres avant le {@code -}) correspond à {@code yearToken}.
     *
     * <p>Robuste face aux données migrées : le tri est <b>numérique</b> (sur la
     * séquence extraite en fin de code), et le filtre d'année porte sur le
     * <b>code lui-même</b> (et non {@code created_at}), ce qui évite de calculer
     * un « prochain » code qui entre en collision avec un code déjà existant.
     *
     * @param branchId  identifiant de la branche
     * @param yearToken les 2 chiffres de l'année (ex. « 26 »)
     * @return la séquence max (0 si aucun code pour l'année)
     */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(regexp_replace(t.code, '^.*-', '') AS INTEGER)), 0)
            FROM test_orders t
            WHERE t.deleted_at IS NULL AND t.branch_id = :branchId
              AND t.code ~ (:yearToken || '-[0-9]+$')
            """, nativeQuery = true)
    int findMaxSequenceForYear(
            @Param("branchId") UUID branchId,
            @Param("yearToken") String yearToken);

    /**
     * Teste l'existence d'un code, <b>y compris sur les bons supprimés</b>
     * (l'index d'unicité de la colonne {@code code} porte sur toutes les lignes).
     * Utilisé en pré-vérification avant l'insertion pour éviter la collision.
     *
     * @param code code candidat
     * @return true si un bon (même supprimé) porte déjà ce code
     */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM test_orders WHERE code = :code)", nativeQuery = true)
    boolean existsByCodeIncludingDeleted(@Param("code") String code);

    // -------------------------------------------------------------------------
    // Myspace — statistiques des bons assignés à un utilisateur
    // -------------------------------------------------------------------------

    /**
     * Compte tous les bons d'examen assignés à un utilisateur pour une branche.
     *
     * @param userId   identifiant de l'utilisateur assigné
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @return nombre total de bons assignés
     */
    @Query("SELECT COUNT(t) FROM TestOrder t WHERE t.branchId = :branchId " +
           "AND (:seeAll = TRUE OR t.assignedToUserId = :userId)")
    long countByAssignedToUserIdAndBranchId(
            @Param("userId") UUID userId,
            @Param("branchId") UUID branchId,
            @Param("seeAll") boolean seeAll);

    /**
     * Compte les bons d'examen assignés à un utilisateur pour une branche et un statut donnés.
     *
     * @param userId   identifiant de l'utilisateur assigné
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @param status   statut à filtrer
     * @return nombre de bons correspondants
     */
    @Query("SELECT COUNT(t) FROM TestOrder t WHERE t.assignedToUserId = :userId AND t.branchId = :branchId AND t.status = :status")
    long countByAssignedToUserIdAndBranchIdAndStatus(
            @Param("userId") UUID userId,
            @Param("branchId") UUID branchId,
            @Param("status") TestOrderStatus status);

    /**
     * Compte les bons d'examen urgents assignés à un utilisateur pour une branche.
     *
     * @param userId   identifiant de l'utilisateur assigné
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @return nombre de bons urgents
     */
    @Query("SELECT COUNT(t) FROM TestOrder t WHERE t.branchId = :branchId AND t.isUrgent = true " +
           "AND (:seeAll = TRUE OR t.assignedToUserId = :userId)")
    long countUrgentByAssignedToUserIdAndBranchId(
            @Param("userId") UUID userId,
            @Param("branchId") UUID branchId,
            @Param("seeAll") boolean seeAll);

    /**
     * Compte les bons d'examen en retard (PENDING assignés il y a plus de 48 h).
     *
     * @param userId          identifiant de l'utilisateur assigné
     * @param branchId        identifiant de la branche (isolation multi-tenant)
     * @param cutoff          date limite — les bons dont {@code assignmentDate} est antérieure
     *                        à cette valeur sont considérés en retard
     * @return nombre de bons en retard
     */
    @Query("SELECT COUNT(t) FROM TestOrder t " +
           "LEFT JOIN com.labo.anapath.report.Report r ON r.testOrder.id = t.id " +
           "WHERE t.branchId = :branchId " +
           "AND (:seeAll = TRUE OR t.assignedToUserId = :userId) " +
           "AND (r IS NULL OR r.status NOT IN (com.labo.anapath.report.ReportStatus.VALIDATED, com.labo.anapath.report.ReportStatus.DELIVERED)) " +
           "AND t.assignmentDate IS NOT NULL AND t.assignmentDate < :cutoff")
    long countLateByAssignedToUserIdAndBranchId(
            @Param("userId") UUID userId,
            @Param("branchId") UUID branchId,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("seeAll") boolean seeAll);

    /**
     * Compte les bons assignés dont le rapport n'est pas encore terminé
     * (rapport inexistant, ou statut autre que VALIDATED/DELIVERED).
     */
    @Query("SELECT COUNT(t) FROM TestOrder t " +
           "LEFT JOIN com.labo.anapath.report.Report r ON r.testOrder.id = t.id " +
           "WHERE t.branchId = :branchId " +
           "AND (:seeAll = TRUE OR t.assignedToUserId = :userId) " +
           "AND (r IS NULL OR r.status NOT IN (com.labo.anapath.report.ReportStatus.VALIDATED, com.labo.anapath.report.ReportStatus.DELIVERED))")
    long countAssignedReportPending(@Param("userId") UUID userId, @Param("branchId") UUID branchId,
                                    @Param("seeAll") boolean seeAll);

    /**
     * Compte les bons assignés dont le rapport est terminé (VALIDATED ou DELIVERED).
     */
    @Query("SELECT COUNT(t) FROM TestOrder t " +
           "JOIN com.labo.anapath.report.Report r ON r.testOrder.id = t.id " +
           "WHERE t.branchId = :branchId " +
           "AND (:seeAll = TRUE OR t.assignedToUserId = :userId) " +
           "AND r.status IN (com.labo.anapath.report.ReportStatus.VALIDATED, com.labo.anapath.report.ReportStatus.DELIVERED)")
    long countAssignedReportDone(@Param("userId") UUID userId, @Param("branchId") UUID branchId,
                                 @Param("seeAll") boolean seeAll);

    /**
     * Compte les bons d'examen immuno assignés dont le rapport n'est pas terminé.
     *
     * @param userId   identifiant de l'utilisateur assigné
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @param typeIds  liste des UUID des types immuno
     * @return nombre de bons immuno en attente
     */
    @Query("SELECT COUNT(t) FROM TestOrder t " +
           "LEFT JOIN com.labo.anapath.report.Report r ON r.testOrder.id = t.id " +
           "WHERE t.branchId = :branchId " +
           "AND (:seeAll = TRUE OR t.assignedToUserId = :userId) " +
           "AND t.typeOrder.id IN :typeIds " +
           "AND (r IS NULL OR r.status NOT IN (com.labo.anapath.report.ReportStatus.VALIDATED, com.labo.anapath.report.ReportStatus.DELIVERED))")
    long countImmunoPendingByAssignedToUserId(
            @Param("userId") UUID userId,
            @Param("branchId") UUID branchId,
            @Param("typeIds") List<UUID> typeIds,
            @Param("seeAll") boolean seeAll);

    // -------------------------------------------------------------------------
    // Myspace — liste paginée des bons assignés à un utilisateur
    // -------------------------------------------------------------------------

    /**
     * Retourne la page de bons d'examen assignés à un utilisateur, avec filtres optionnels
     * sur le statut et une recherche textuelle (code du bon ou nom du patient).
     *
     * @param userId   identifiant de l'utilisateur assigné
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @param status   statut à filtrer (peut être null pour tous les statuts)
     * @param search   motif de recherche insensible à la casse (peut être null)
     * @param pageable paramètres de pagination
     * @return page de bons correspondants
     */
    @Query(value = """
            SELECT t.* FROM test_orders t
            JOIN patients pat ON pat.id = t.patient_id AND pat.deleted_at IS NULL
            LEFT JOIN reports r ON r.test_order_id = t.id AND r.deleted_at IS NULL
            WHERE t.deleted_at IS NULL
              AND (:seeAll = TRUE OR t.assigned_to_user_id = :userId)
              AND t.branch_id = :branchId
              AND (:status IS NULL
                   OR (:status = 'PENDING'   AND (r.id IS NULL OR r.status NOT IN ('VALIDATED', 'DELIVERED')))
                   OR (:status = 'VALIDATED' AND r.status IN ('VALIDATED', 'DELIVERED'))
                   OR (:status = 'DELIVERED' AND r.status = 'DELIVERED'))
              AND (CAST(:typeOrderId AS uuid) IS NULL OR t.type_order_id = CAST(:typeOrderId AS uuid))
              AND (:priority IS NULL
                   OR (:priority = 'urgent' AND t.is_urgent = true)
                   OR (:priority = 'late' AND t.status::text = 'PENDING'
                       AND t.assignment_date IS NOT NULL
                       AND t.assignment_date < (now() - interval '21 days')))
              AND (CAST(:fromDate AS date) IS NULL OR DATE(t.created_at) >= CAST(:fromDate AS date))
              AND (CAST(:toDate AS date) IS NULL OR DATE(t.created_at) <= CAST(:toDate AS date))
              AND (:search IS NULL OR (
                    lower(coalesce(t.code,         '')) LIKE lower('%' || CAST(:search AS text) || '%')
                 OR lower(coalesce(pat.firstname,  '')) LIKE lower('%' || CAST(:search AS text) || '%')
                 OR lower(coalesce(pat.lastname,   '')) LIKE lower('%' || CAST(:search AS text) || '%')
              ))
            ORDER BY t.created_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM test_orders t
            JOIN patients pat ON pat.id = t.patient_id AND pat.deleted_at IS NULL
            LEFT JOIN reports r ON r.test_order_id = t.id AND r.deleted_at IS NULL
            WHERE t.deleted_at IS NULL
              AND (:seeAll = TRUE OR t.assigned_to_user_id = :userId)
              AND t.branch_id = :branchId
              AND (:status IS NULL
                   OR (:status = 'PENDING'   AND (r.id IS NULL OR r.status NOT IN ('VALIDATED', 'DELIVERED')))
                   OR (:status = 'VALIDATED' AND r.status IN ('VALIDATED', 'DELIVERED'))
                   OR (:status = 'DELIVERED' AND r.status = 'DELIVERED'))
              AND (CAST(:typeOrderId AS uuid) IS NULL OR t.type_order_id = CAST(:typeOrderId AS uuid))
              AND (:priority IS NULL
                   OR (:priority = 'urgent' AND t.is_urgent = true)
                   OR (:priority = 'late' AND t.status::text = 'PENDING'
                       AND t.assignment_date IS NOT NULL
                       AND t.assignment_date < (now() - interval '21 days')))
              AND (CAST(:fromDate AS date) IS NULL OR DATE(t.created_at) >= CAST(:fromDate AS date))
              AND (CAST(:toDate AS date) IS NULL OR DATE(t.created_at) <= CAST(:toDate AS date))
              AND (:search IS NULL OR (
                    lower(coalesce(t.code,         '')) LIKE lower('%' || CAST(:search AS text) || '%')
                 OR lower(coalesce(pat.firstname,  '')) LIKE lower('%' || CAST(:search AS text) || '%')
                 OR lower(coalesce(pat.lastname,   '')) LIKE lower('%' || CAST(:search AS text) || '%')
              ))
            """,
            nativeQuery = true)
    Page<TestOrder> findMyspaceOrders(
            @Param("userId") UUID userId,
            @Param("branchId") UUID branchId,
            @Param("status") String status,
            @Param("typeOrderId") String typeOrderId,
            @Param("priority") String priority,
            @Param("fromDate") String fromDate,
            @Param("toDate") String toDate,
            @Param("search") String search,
            @Param("seeAll") boolean seeAll,
            Pageable pageable);

    // -------------------------------------------------------------------------
    // Immunohistochimie — comptage des bons en attente (rapport DRAFT ou inexistant)
    // -------------------------------------------------------------------------

    /**
     * Compte les bons d'examen immuno de la branche dont le rapport associé
     * est en statut {@code DRAFT} ou n'existe pas encore.
     *
     * @param branchId identifiant de la branche
     * @param typeIds  liste des UUID des types immuno
     * @return nombre de bons immuno en attente
     */
    @Query("""
            SELECT COUNT(t) FROM TestOrder t
            LEFT JOIN com.labo.anapath.report.Report r ON r.testOrder.id = t.id
            WHERE t.branchId = :branchId
              AND t.typeOrder.id IN :typeIds
              AND (r.status = com.labo.anapath.report.ReportStatus.DRAFT OR r IS NULL)
            """)
    long countImmunoPending(@Param("branchId") UUID branchId,
                            @Param("typeIds") List<UUID> typeIds);
}

package com.labo.anapath.testorder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestOrderAssignmentDetailRepository extends JpaRepository<TestOrderAssignmentDetail, UUID> {

    @Query("SELECT CASE WHEN COUNT(d) > 0 THEN TRUE ELSE FALSE END FROM TestOrderAssignmentDetail d WHERE d.testOrder.id = :testOrderId")
    boolean existsByTestOrderId(@Param("testOrderId") UUID testOrderId);

    @Query("SELECT d FROM TestOrderAssignmentDetail d WHERE d.testOrder.id = :testOrderId")
    Optional<TestOrderAssignmentDetail> findByTestOrderId(@Param("testOrderId") UUID testOrderId);

    /**
     * Nom de la personne affectée, pour un lot de bons d'examen.
     * <p>
     * Chargement groupé : la liste des demandes résout une page entière en une
     * requête, au lieu d'une par ligne. C'est le motif déjà employé pour les
     * comptes rendus et les factures dans {@code TestOrderServiceImpl.findAll}.
     * </p>
     * <p>
     * {@code DISTINCT ON} ne retient qu'une affectation par bon, la plus
     * récente. La relation n'est pas garantie unique — relevé en production, un
     * bon figure dans deux affectations. Sans ce filtre, le bon apparaîtrait
     * deux fois dans la correspondance et le nom retenu dépendrait de l'ordre
     * de parcours.
     * </p>
     *
     * @param testOrderIds identifiants des bons de la page courante
     * @return lignes {@code [testOrderId, nom complet]}
     */
    @Query(value = """
            SELECT DISTINCT ON (d.test_order_id)
                   d.test_order_id,
                   TRIM(COALESCE(u.firstname, '') || ' ' || COALESCE(u.lastname, ''))
            FROM test_order_assignment_details d
            JOIN test_order_assignments a ON a.id = d.test_order_assignment_id
            JOIN users u ON u.id = a.user_id
            WHERE d.test_order_id IN (:testOrderIds)
              AND d.deleted_at IS NULL
              AND a.deleted_at IS NULL
            ORDER BY d.test_order_id, a.created_at DESC
            """, nativeQuery = true)
    List<Object[]> findAssignedUserNames(@Param("testOrderIds") List<UUID> testOrderIds);
}

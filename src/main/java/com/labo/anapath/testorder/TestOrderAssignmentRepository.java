package com.labo.anapath.testorder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TestOrderAssignmentRepository extends JpaRepository<TestOrderAssignment, UUID> {

    long countByBranchId(UUID branchId);

    /**
     * La plus haute séquence atteinte cette année, lue dans les codes eux-mêmes.
     *
     * <p>Numériquement, et non par tri texte : « 0009 » précède « 0010 », ce
     * qu'un ordre alphabétique respecte tant que la largeur est fixe mais qui
     * cède dès qu'un code hérité en sort.</p>
     *
     * <p>L'année vient du <b>code</b>, pas de {@code created_at} : une
     * affectation saisie le 2 janvier pour la veille garde son numéro de
     * l'année écoulée.</p>
     *
     * <p>Les lignes supprimées comptent. C'est délibéré : leur numéro a été
     * porté sur un bordereau, le réattribuer créerait deux affectations
     * homonymes dans les archives.</p>
     */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(regexp_replace(a.code, '^.*-', '') AS INTEGER)), 0)
            FROM test_order_assignments a
            WHERE a.branch_id = :branchId
              AND a.code ~ (:yearToken || '-[0-9]+$')
            """, nativeQuery = true)
    int findMaxSequenceForYear(@Param("branchId") UUID branchId,
                               @Param("yearToken") String yearToken);

    /** Y compris les lignes supprimées : un code retiré reste pris. */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM test_order_assignments WHERE code = :code)",
            nativeQuery = true)
    boolean existsByCodeIncludingDeleted(@Param("code") String code);

    @Query("SELECT DISTINCT a FROM TestOrderAssignment a JOIN a.details d JOIN d.testOrder o JOIN o.typeOrder ty " +
           "WHERE a.branchId = :branchId AND ty.slug IN ('histologie','cytologie','biopsie','pièce-opératoire') " +
           "ORDER BY a.createdAt DESC")
    Page<TestOrderAssignment> findHistoCyto(@Param("branchId") UUID branchId, Pageable pageable);

    @Query("SELECT DISTINCT a FROM TestOrderAssignment a JOIN a.details d JOIN d.testOrder o JOIN o.typeOrder ty " +
           "WHERE a.branchId = :branchId AND ty.slug IN ('immuno-interne','immuno-exterme') " +
           "ORDER BY a.createdAt DESC")
    Page<TestOrderAssignment> findImmuno(@Param("branchId") UUID branchId, Pageable pageable);
}

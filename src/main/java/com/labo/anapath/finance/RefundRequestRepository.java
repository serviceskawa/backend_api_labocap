package com.labo.anapath.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {

    Page<RefundRequest> findByBranchId(UUID branchId, Pageable pageable);

    boolean existsByInvoiceId(UUID invoiceId);

    Optional<RefundRequest> findByInvoiceId(UUID invoiceId);

    /** Badge « Remboursements » : demandes encore en attente de traitement. */
    long countByBranchIdAndStatus(UUID branchId, String status);

    /**
     * Dernier code attribué à une demande sur l'année, pour prolonger la séquence.
     * Laravel calcule ce numéro sur les demandes elles-mêmes, pas sur les factures.
     */
    @Query("SELECT r FROM RefundRequest r WHERE r.branchId = :branchId "
            + "AND r.code IS NOT NULL AND YEAR(r.createdAt) = :year ORDER BY r.code DESC")
    List<RefundRequest> findLastCodeOfYear(@Param("branchId") UUID branchId,
                                           @Param("year") int year,
                                           Pageable pageable);
}

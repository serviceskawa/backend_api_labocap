package com.labo.anapath.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CashboxVoucherRepository extends JpaRepository<CashboxVoucher, UUID> {

    Page<CashboxVoucher> findByBranchId(UUID branchId, Pageable pageable);

    Page<CashboxVoucher> findByBranchIdAndStatus(UUID branchId, String status, Pageable pageable);

    /**
     * Compte les bons de caisse encore en attente de traitement, pour le badge
     * « Caisses » du menu — équivalent du helper Laravel
     * {@code getnbrBonCaissePending()} (bon non encore payé).
     *
     * @param branchId identifiant de la branche
     * @return nombre de bons au statut « en attente »
     */
    long countByBranchIdAndStatus(UUID branchId, String status);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(v) FROM CashboxVoucher v WHERE v.branchId = :branchId AND v.createdAt >= :startOfDay AND v.createdAt < :endOfDay")
    long countByBranchIdBetween(
            @org.springframework.data.repository.query.Param("branchId") UUID branchId,
            @org.springframework.data.repository.query.Param("startOfDay") java.time.LocalDateTime startOfDay,
            @org.springframework.data.repository.query.Param("endOfDay") java.time.LocalDateTime endOfDay);
}

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

    /**
     * Plus grand numéro d'ordre attribué à un bon de caisse de l'année en cours
     * — voir {@code generateCodeTicket()} de Laravel (format {@code BC25-0001}).
     *
     * <p>Requête native : elle doit voir aussi les bons supprimés
     * (suppression logique), sous peine de réattribuer un code déjà pris.</p>
     *
     * @param yearPrefix préfixe de l'année, p. ex. {@code BC25-}
     * @return le plus grand numéro d'ordre, ou {@code null} si aucun bon cette année
     */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT MAX(CAST(RIGHT(code, 4) AS INTEGER))
            FROM cashbox_vouchers
            WHERE code LIKE :yearPrefix || '____'
              AND RIGHT(code, 4) ~ '^[0-9][0-9][0-9][0-9]$'
            """, nativeQuery = true)
    Integer findMaxSequenceForYear(
            @org.springframework.data.repository.query.Param("yearPrefix") String yearPrefix);
}

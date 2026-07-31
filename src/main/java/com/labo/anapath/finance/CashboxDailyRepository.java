package com.labo.anapath.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CashboxDailyRepository extends JpaRepository<CashboxDaily, UUID> {

    Page<CashboxDaily> findByBranchId(UUID branchId, Pageable pageable);

    @Query("SELECT d FROM CashboxDaily d WHERE d.branchId = :branchId AND d.cashbox.id = :cashboxId AND d.date = :date")
    Optional<CashboxDaily> findByBranchIdAndCashboxIdAndDate(
            @Param("branchId") UUID branchId,
            @Param("cashboxId") UUID cashboxId,
            @Param("date") LocalDate date);

    Optional<CashboxDaily> findFirstByBranchIdAndStatusOrderByUpdatedAtDesc(UUID branchId, Integer status);

    @Query("SELECT COALESCE(SUM(o.amount), 0) FROM CashboxOperation o WHERE o.branchId = :branchId AND o.type = 'CREDIT' AND o.paymentMethod = :method AND o.createdAt >= :sinceDate")
    BigDecimal sumCreditByPaymentMethod(
            @Param("branchId") UUID branchId,
            @Param("method") String method,
            @Param("sinceDate") LocalDateTime sinceDate);

    /**
     * Plus grand numéro d'ordre attribué à une ouverture de caisse de l'année en
     * cours — voir {@code generateCodeOpeningCashbox()} de Laravel (format
     * {@code OC250001}).
     *
     * <p>Requête native : elle doit voir aussi les sessions supprimées
     * (suppression logique), sous peine de réattribuer un code déjà pris.</p>
     *
     * @param yearPrefix préfixe de l'année, p. ex. {@code OC26}
     * @return le plus grand numéro d'ordre, ou {@code null} si aucune cette année
     */
    @Query(value = """
            SELECT MAX(CAST(RIGHT(code, 4) AS INTEGER))
            FROM cashbox_dailies
            WHERE code LIKE :yearPrefix || '____'
              AND RIGHT(code, 4) ~ '^[0-9][0-9][0-9][0-9]$'
            """, nativeQuery = true)
    Integer findMaxSequenceForYear(@Param("yearPrefix") String yearPrefix);
}

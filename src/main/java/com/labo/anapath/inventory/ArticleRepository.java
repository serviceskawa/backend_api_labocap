package com.labo.anapath.inventory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArticleRepository extends JpaRepository<Article, UUID> {
    Page<Article> findByBranchId(UUID branchId, Pageable pageable);
    Optional<Article> findFirstByBranchIdAndName(UUID branchId, String name);
    List<Article> findByBranchIdAndNameContainingIgnoreCase(UUID branchId, String name);
    long countByBranchIdAndQuantity(UUID branchId, BigDecimal quantity);

    @Query("SELECT COUNT(a) FROM Article a WHERE a.branchId = :branchId AND a.quantity > 0 AND a.quantity < a.minimumStock")
    long countLowStock(@Param("branchId") UUID branchId);

    /**
     * Nombre d'articles ayant atteint le stock minimum, au sens du badge « Stocks »
     * de Laravel (helper {@code getnbrStockMinim} : {@code quantity <= minimum_stock}).
     * Se distingue de {@link #countLowStock} qui exclut les articles en rupture.
     */
    @Query("SELECT COUNT(a) FROM Article a WHERE a.branchId = :branchId AND a.quantity <= a.minimumStock")
    long countStockMinimumReached(@Param("branchId") UUID branchId);
}

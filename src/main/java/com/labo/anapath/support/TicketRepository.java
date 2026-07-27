package com.labo.anapath.support;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository Spring Data JPA pour l'accès aux tickets de support.
 */
@Repository
public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /**
     * Retourne une page de tickets filtrés par filiale.
     *
     * @param branchId identifiant de la filiale
     * @param pageable paramètres de pagination
     * @return page de tickets
     */
    Page<Ticket> findByBranchId(UUID branchId, Pageable pageable);

    /**
     * Compte les tickets encore ouverts, pour le badge « Signaler un problème » du menu.
     *
     * <p>Reprise du helper Laravel {@code getnbrTicketPending($userId)} : un super-admin
     * (rôle {@code rootuser} côté Laravel) voit tous les tickets ouverts, les autres
     * utilisateurs uniquement les leurs.</p>
     *
     * @param branchId identifiant de la filiale (isolation multi-tenant)
     * @param userId   utilisateur connecté
     * @param seeAll   {@code true} pour compter les tickets de tous les utilisateurs
     * @return nombre de tickets au statut {@code OPEN}
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT COUNT(t) FROM Ticket t
            WHERE t.branchId = :branchId
              AND t.status = com.labo.anapath.support.TicketStatus.OPEN
              AND (:seeAll = TRUE OR t.user.id = :userId)
            """)
    long countOpen(
            @org.springframework.data.repository.query.Param("branchId") UUID branchId,
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("seeAll") boolean seeAll);
}

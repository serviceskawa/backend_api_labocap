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

    /**
     * Plus grand numéro d'ordre attribué à un ticket de l'année en cours.
     *
     * <p>Reprend {@code generateCodeTicket()} de Laravel : le code vaut
     * {@code TI-} + les deux derniers chiffres de l'année + un compteur sur
     * 4 chiffres, remis à 0001 à chaque nouvelle année. Le compteur est donc lu
     * sur les 4 derniers caractères du code, parmi les seuls tickets de l'année.</p>
     *
     * @param yearPrefix préfixe de l'année, p. ex. {@code TI-25}
     * @return le plus grand numéro d'ordre, ou {@code null} si aucun ticket cette année
     */
    // `LIKE prefixe || '____'` borne le code à exactement 4 caractères après le
    // préfixe ; le regex (sans accolades, qui seraient prises pour une séquence
    // d'échappement JDBC) garantit qu'ils sont numériques avant la conversion.
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT MAX(CAST(RIGHT(ticket_code, 4) AS INTEGER))
            FROM tickets
            WHERE ticket_code LIKE :yearPrefix || '____'
              AND RIGHT(ticket_code, 4) ~ '^[0-9][0-9][0-9][0-9]$'
            """, nativeQuery = true)
    Integer findMaxSequenceForYear(
            @org.springframework.data.repository.query.Param("yearPrefix") String yearPrefix);
}

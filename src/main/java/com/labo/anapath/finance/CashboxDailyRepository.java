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

    /**
     * Session ouverte juste après celle dont on donne l'instant de création.
     *
     * <p>Sert de borne haute au calcul de fermeture. Sans elle, la fenêtre d'une
     * session n'a pas de fin : une session du 07/08 encore ouverte compte
     * l'argent encaissé après l'ouverture de celle du 10/08 — argent que la
     * fermeture de cette dernière comptera une seconde fois.</p>
     *
     * <p>Chaque session couvre ainsi exactement l'intervalle qui la sépare de la
     * suivante. Aucun franc ne tombe entre deux sessions, aucun n'est compté
     * deux fois. Lorsqu'il n'y a pas de session suivante — le cas courant, celle
     * qu'on ferme étant la dernière — la fenêtre reste ouverte jusqu'à
     * maintenant.</p>
     */
    Optional<CashboxDaily> findFirstByBranchIdAndCreatedAtGreaterThanOrderByCreatedAtAsc(
            UUID branchId, LocalDateTime createdAt);

    /**
     * Encaissements de la session en cours pour un mode de paiement donné.
     *
     * <p>Somme les <b>factures</b>, comme Laravel. {@code
     * CashboxDailyController@detail_fermeture_caisse} additionnait les
     * {@code cashbox_adds} <i>rattachés à une facture</i> dont le {@code payment}
     * correspond ({@code whereHas('invoice')}), et le montant d'un
     * {@code cashbox_add} valait précisément {@code invoice.total}. La somme
     * revient donc au total des factures réglées depuis l'ouverture.</p>
     *
     * <p>La version précédente sommait les opérations de caisse. Deux défauts en
     * découlaient : elle dépendait d'une écriture qui n'existait pas encore
     * (d'où un solde de fermeture à zéro), et les opérations reprises de la
     * migration ont {@code invoice_id} et {@code payment_method} à NULL, donc
     * aucune journée antérieure n'aurait pu être ventilée. Partir des factures
     * lève les deux : la donnée est là depuis toujours.</p>
     *
     * <p>{@code status_invoice = 0} retient les factures de vente et écarte les
     * avoirs — l'équivalent du {@code cashbox_id = 2} de Laravel, qui visait la
     * caisse de vente. {@code updated_at} est l'instant du règlement : c'est la
     * sauvegarde opérée par {@code markAsPaid}.</p>
     */
    @Query(value = """
            SELECT COALESCE(SUM(i.total), 0)
            FROM invoices i
            WHERE i.branch_id = :branchId
              AND i.deleted_at IS NULL
              AND i.paid = true
              AND i.status_invoice = 0
              AND i.payment = :method
              AND i.updated_at >= :sinceDate
              AND (CAST(:untilDate AS timestamp) IS NULL OR i.updated_at < CAST(:untilDate AS timestamp))
            """, nativeQuery = true)
    BigDecimal sumCreditByPaymentMethod(
            @Param("branchId") UUID branchId,
            @Param("method") String method,
            @Param("sinceDate") LocalDateTime sinceDate,
            @Param("untilDate") LocalDateTime untilDate);

    /**
     * Nombre de règlements de la session pour un mode de paiement donné.
     *
     * <p>Mêmes critères et même fenêtre que {@link #sumCreditByPaymentMethod} —
     * c'est tout l'objet de cette requête. L'écran de fermeture comptait
     * auparavant les opérations de caisse, filtrées sur la seule date de la
     * session : le nombre affiché n'avait donc ni la même source ni la même
     * période que le montant placé juste à côté. Une session ouverte depuis
     * plusieurs jours affichait « 0 » en face de plusieurs millions.</p>
     */
    @Query(value = """
            SELECT COUNT(*)
            FROM invoices i
            WHERE i.branch_id = :branchId
              AND i.deleted_at IS NULL
              AND i.paid = true
              AND i.status_invoice = 0
              AND i.payment = :method
              AND i.updated_at >= :sinceDate
              AND (CAST(:untilDate AS timestamp) IS NULL OR i.updated_at < CAST(:untilDate AS timestamp))
            """, nativeQuery = true)
    long countCreditByPaymentMethod(
            @Param("branchId") UUID branchId,
            @Param("method") String method,
            @Param("sinceDate") LocalDateTime sinceDate,
            @Param("untilDate") LocalDateTime untilDate);

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

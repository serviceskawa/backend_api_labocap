package com.labo.anapath.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository Spring Data JPA pour l'entité {@link TitleReport}.
 */
@Repository
public interface TitleReportRepository extends JpaRepository<TitleReport, UUID> {

    /**
     * Retourne la liste paginée des titres de rapport d'une branche.
     * Assure l'isolation multi-tenant.
     *
     * @param branchId identifiant de la branche
     * @param pageable paramètres de pagination
     * @return page de titres de rapport
     */
    Page<TitleReport> findByBranchId(UUID branchId, Pageable pageable);

    /**
     * Désactive le flag {@code isDefault} sur tous les titres de rapport d'une branche.
     * Utilisé pour garantir qu'un seul titre puisse être marqué comme défaut par branche.
     *
     * @param branchId identifiant de la branche
     */
    @Modifying
    @Transactional
    @Query("UPDATE TitleReport t SET t.isDefault = false WHERE t.branchId = :branchId")
    void unsetDefaultForBranch(@Param("branchId") UUID branchId);

    /**
     * Désactive le flag {@code isDefault} sur tous les titres d'une branche SAUF celui indiqué.
     *
     * <p>{@code flushAutomatically = true} : les changements en attente sur l'entité courante
     * (dont son passage à {@code isDefault=true}) sont écrits AVANT ce bulk update.
     * {@code clearAutomatically = true} : vide le contexte de persistance après, pour éviter
     * qu'un instantané Hibernate périmé ne réécrive une valeur incorrecte au commit.
     *
     * @param branchId identifiant de la branche
     * @param exceptId identifiant du titre à conserver comme défaut
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("UPDATE TitleReport t SET t.isDefault = false WHERE t.branchId = :branchId AND t.id <> :exceptId")
    void unsetDefaultForBranchExcept(@Param("branchId") UUID branchId, @Param("exceptId") UUID exceptId);

    /**
     * Retourne le titre de rapport marqué comme "par défaut" pour la branche donnée, s'il existe.
     *
     * @param branchId identifiant de la branche
     * @return le titre par défaut, ou {@link Optional#empty()} si aucun n'est défini
     */
    Optional<TitleReport> findFirstByBranchIdAndIsDefaultTrue(UUID branchId);
}

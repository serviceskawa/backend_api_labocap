package com.labo.anapath.doc;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocRepository extends JpaRepository<Doc, UUID> {

    Page<Doc> findByBranchId(UUID branchId, Pageable pageable);

    /**
     * Documents partagés avec l'utilisateur : ceux dont le rôle de partage
     * ({@code role_id}) fait partie des rôles de l'utilisateur. Réplique Laravel
     * {@code doc_share} (filtre {@code userCheckRole(doc.role_id)}), en SQL.
     */
    @Query(value = """
            SELECT d.* FROM docs d
            WHERE d.deleted_at IS NULL
              AND d.branch_id = :branchId
              AND d.role_id IN (SELECT ur.role_id FROM user_roles ur WHERE ur.user_id = :userId)
            ORDER BY d.created_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM docs d
            WHERE d.deleted_at IS NULL
              AND d.branch_id = :branchId
              AND d.role_id IN (SELECT ur.role_id FROM user_roles ur WHERE ur.user_id = :userId)
            """,
            nativeQuery = true)
    Page<Doc> findSharedWithMe(@Param("userId") UUID userId,
                               @Param("branchId") UUID branchId,
                               Pageable pageable);

    /**
     * Corbeille : documents supprimés logiquement (deleted_at non nul) de la branche.
     * Requête native pour contourner le {@code @SQLRestriction("deleted_at IS NULL")}.
     */
    @Query(value = """
            SELECT d.* FROM docs d
            WHERE d.deleted_at IS NOT NULL
              AND d.branch_id = :branchId
            ORDER BY d.deleted_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM docs d
            WHERE d.deleted_at IS NOT NULL
              AND d.branch_id = :branchId
            """,
            nativeQuery = true)
    Page<Doc> findTrashed(@Param("branchId") UUID branchId, Pageable pageable);

    /** Récupère un document quel que soit son état (y compris en corbeille). */
    @Query(value = "SELECT * FROM docs WHERE id = :id", nativeQuery = true)
    Optional<Doc> findAnyById(@Param("id") UUID id);

    /** Restaure un document de la corbeille (remet {@code deleted_at} à nul). */
    @Modifying
    @Query(value = "UPDATE docs SET deleted_at = NULL WHERE id = :id", nativeQuery = true)
    int restoreById(@Param("id") UUID id);

    /** Suppression physique des versions d'un document (préalable au hard delete). */
    @Modifying
    @Query(value = "DELETE FROM doc_versions WHERE doc_id = :id", nativeQuery = true)
    void hardDeleteVersions(@Param("id") UUID id);

    /** Suppression physique définitive du document. */
    @Modifying
    @Query(value = "DELETE FROM docs WHERE id = :id", nativeQuery = true)
    void hardDeleteDoc(@Param("id") UUID id);
}

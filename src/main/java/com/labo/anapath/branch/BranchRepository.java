package com.labo.anapath.branch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository Spring Data JPA pour l'accès aux données des agences ({@link Branch}).
 * <p>
 * Fournit les requêtes personnalisées nécessaires au contrôle d'unicité du nom
 * lors de la création et de la mise à jour.
 * </p>
 */
@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {

    /**
     * Vérifie qu'il n'existe pas déjà une agence portant ce nom (insensible à la casse).
     *
     * @param name nom à vérifier
     * @return {@code true} si une agence avec ce nom existe déjà
     */
    boolean existsByNameIgnoreCase(String name);

    /**
     * Vérifie l'unicité du nom en excluant l'agence dont l'identifiant est fourni.
     * Utilisé lors d'une mise à jour pour ne pas bloquer l'agence sur son propre nom.
     *
     * @param name nom à vérifier
     * @param id   identifiant de l'agence à exclure de la vérification
     * @return {@code true} si une autre agence porte déjà ce nom
     */
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    /**
     * Vérifie que l'utilisateur a bien accès à la branche demandée, selon la règle
     * exacte du middleware {@code BranchRequired} de Laravel : une ligne pivot
     * {@code branch_user} doit exister avec {@code is_default = true}, non supprimée
     * ({@code deleted_at IS NULL}), et la branche elle-même ne doit pas être supprimée.
     *
     * @param userId   UUID de l'utilisateur connecté
     * @param branchId UUID de la branche sélectionnée (en-tête {@code X-Branch-Id})
     * @return {@code true} si l'accès est accordé
     */
    @Query(value = """
            SELECT EXISTS (
                SELECT 1
                FROM branch_user bu
                JOIN branches b ON b.id = bu.branch_id
                WHERE bu.user_id = :userId
                  AND bu.branch_id = :branchId
                  AND bu.is_default = true
                  AND bu.deleted_at IS NULL
                  AND b.deleted_at IS NULL
            )
            """, nativeQuery = true)
    boolean hasBranchAccess(@Param("userId") UUID userId, @Param("branchId") UUID branchId);

    /**
     * Liste les branches accessibles par l'utilisateur pour l'écran de sélection,
     * branche(s) par défaut en tête. Chaque ligne : {@code [id, name, code, location, is_default]}.
     *
     * @param userId UUID de l'utilisateur connecté
     * @return lignes brutes des branches accessibles (non supprimées)
     */
    @Query(value = """
            SELECT b.id, b.name, b.code, b.location, bu.is_default
            FROM branch_user bu
            JOIN branches b ON b.id = bu.branch_id
            WHERE bu.user_id = :userId
              AND bu.deleted_at IS NULL
              AND b.deleted_at IS NULL
            ORDER BY bu.is_default DESC, b.name ASC
            """, nativeQuery = true)
    List<Object[]> findAccessibleBranches(@Param("userId") UUID userId);
}

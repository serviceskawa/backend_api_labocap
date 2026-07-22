package com.labo.anapath.user;

import com.labo.anapath.dashboard.DashboardDto;
import com.labo.anapath.dashboard.DashboardProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository JPA pour l'entité {@link User}.
 *
 * <p>Les requêtes de recherche et d'existence sont automatiquement filtrées
 * par la restriction Hibernate {@code deleted_at IS NULL} définie sur l'entité,
 * garantissant que les utilisateurs supprimés logiquement sont invisibles.</p>
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Recherche un utilisateur actif par son adresse e-mail.
     *
     * @param email adresse e-mail à rechercher
     * @return un {@link Optional} contenant l'utilisateur trouvé, ou vide
     */
    Optional<User> findByEmail(String email);

    /**
     * Recherche un utilisateur par son identifiant et sa succursale.
     * Assure l'isolation multi-tenant : un utilisateur ne peut être accédé que par sa succursale.
     *
     * @param id       identifiant UUID de l'utilisateur
     * @param branchId identifiant de la succursale
     * @return l'utilisateur s'il appartient à la succursale, sinon vide
     */
    Optional<User> findByIdAndBranchId(UUID id, UUID branchId);

    /**
     * Vérifie si un utilisateur actif existe avec l'adresse e-mail donnée.
     * Utilisé pour détecter les doublons avant création ou modification.
     *
     * @param email adresse e-mail à vérifier
     * @return {@code true} si l'e-mail est déjà utilisé
     */
    boolean existsByEmail(String email);

    /**
     * Retourne la liste paginée des utilisateurs d'une succursale donnée.
     *
     * @param branchId identifiant de la succursale
     * @param pageable paramètres de pagination et de tri
     * @return page d'utilisateurs
     */
    Page<User> findByBranchId(UUID branchId, Pageable pageable);

    /**
     * Vérifie si au moins un utilisateur est rattaché à la succursale donnée.
     *
     * @param branchId identifiant de la succursale
     * @return {@code true} si la succursale possède des utilisateurs
     */
    boolean existsByBranchId(UUID branchId);

    /**
     * Recherche un utilisateur par son token de réinitialisation de mot de passe.
     *
     * @param resetToken token UUID de réinitialisation
     * @return un {@link Optional} contenant l'utilisateur trouvé, ou vide
     */
    Optional<User> findByResetToken(String resetToken);

    /**
     * Indique si au moins un utilisateur actif (non supprimé) est rattaché au rôle donné.
     * Sert à empêcher la suppression d'un rôle encore attribué à des utilisateurs.
     *
     * @param roleId identifiant du rôle
     * @return {@code true} si le rôle est lié à au moins un utilisateur actif
     */
    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.roles r WHERE r.id = :roleId")
    boolean existsByRoleId(@Param("roleId") UUID roleId);

    /**
     * Indique si l'utilisateur possède le rôle « Super Admin » (slug {@code super-admin}).
     * <p>
     * Utilisé par « Mon espace » pour élargir la vue d'un super-admin à l'ensemble
     * des bons d'examen de la branche active, et non à ses seules assignations.
     * </p>
     *
     * @param userId identifiant de l'utilisateur
     * @return {@code true} si l'utilisateur est super-admin
     */
    @Query("SELECT COUNT(u) > 0 FROM User u JOIN u.roles r WHERE u.id = :userId AND r.slug = 'super-admin'")
    boolean isSuperAdmin(@Param("userId") UUID userId);

    // Dashboard — utilisateurs connectés
    @Query(value = """
            SELECT u.id::text as id, u.lastname as lastname, u.firstname as firstname, u.email as email
            FROM users u
            WHERE u.branch_id = :branchId AND u.is_connect = true
              AND u.deleted_at IS NULL
            ORDER BY u.updated_at DESC
            """, nativeQuery = true)
    List<DashboardProjection.ConnectedUser> findConnectedUsersByBranchId(@Param("branchId") UUID branchId);
}

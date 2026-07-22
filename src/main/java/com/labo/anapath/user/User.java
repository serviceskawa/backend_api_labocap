package com.labo.anapath.user;

import com.labo.anapath.branch.Branch;
import com.labo.anapath.common.audit.AuditableEntity;
import com.labo.anapath.role.Permission;
import com.labo.anapath.role.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité représentant un compte utilisateur du LIS.
 *
 * <p>Un utilisateur peut être un technicien, un pathologiste ou un administrateur.
 * Il est rattaché à une succursale (branchId) et peut posséder plusieurs rôles RBAC.
 * La suppression est logique (soft-delete via {@code deleted_at}).</p>
 */
@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class User extends AuditableEntity {

    /** Prénom de l'utilisateur. */
    @Column(name = "firstname", nullable = false, length = 200)
    private String firstname;

    /** Nom de famille de l'utilisateur. */
    @Column(name = "lastname", nullable = false, length = 200)
    private String lastname;

    /** Adresse e-mail unique servant d'identifiant de connexion. */
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    /** Mot de passe haché en BCrypt. */
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    /** Numéro de téléphone (optionnel). Correspond au champ « Telephone » Laravel. */
    @Column(name = "phone", length = 20)
    private String phone;

    /** Numéro WhatsApp (optionnel). */
    @Column(name = "whatsapp", length = 255)
    private String whatsapp;

    /** Taux de commission en pourcentage (0–100). */
    @Column(name = "commission", precision = 10, scale = 2)
    private java.math.BigDecimal commission;

    /** Indique si le compte est actif. Un compte désactivé ne peut pas se connecter. */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    /** Indique si l'authentification à deux facteurs TOTP est activée. */
    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled = false;

    /** Secret TOTP utilisé pour la génération des codes 2FA. */
    @Column(name = "two_factor_secret", length = 255)
    private String twoFactorSecret;

    /** Indique si l'utilisateur est actuellement connecté. */
    @Column(name = "is_connect", nullable = false)
    private boolean isConnect = false;

    /** Code OTP temporaire pour la vérification en deux étapes. */
    @Column(name = "opt")
    private Integer opt;

    /** Informations sur le dernier appareil utilisé pour la connexion. */
    @Column(name = "lastlogindevice", length = 255)
    private String lastLoginDevice;

    /** Signature numérique du praticien (stockée en texte, ex. base64 d'une image). */
    @Column(name = "signature", columnDefinition = "TEXT")
    private String signature;

    /** Indique si l'utilisateur souhaite recevoir des notifications par e-mail. */
    @Column(name = "email_notification", nullable = false)
    private boolean emailNotification = false;

    /** Token de réinitialisation du mot de passe (UUID généré, usage unique). */
    @Column(name = "reset_token", length = 255)
    private String resetToken;

    /** Date d'expiration du token de réinitialisation (1 heure après génération). */
    @Column(name = "reset_token_expires_at")
    private LocalDateTime resetTokenExpiresAt;

    /**
     * Rôles attribués à l'utilisateur.
     * Chargement paresseux : la liste n'est récupérée que si explicitement accédée.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private List<Role> roles = new ArrayList<>();

    /**
     * Permissions directement assignées à l'utilisateur (sans passer par un rôle).
     * Chargement paresseux via la table de jointure {@code users_permissions}.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "users_permissions",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private List<Permission> directPermissions = new ArrayList<>();

    /**
     * Branches accessibles à l'utilisateur (calque du pivot {@code branch_user}
     * de Laravel). La branche d'attache ({@code branchId}) reste la branche
     * effective utilisée pour l'isolation des données.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "branch_user",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "branch_id")
    )
    private List<Branch> branches = new ArrayList<>();
}

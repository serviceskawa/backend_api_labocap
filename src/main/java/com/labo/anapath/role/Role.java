package com.labo.anapath.role;

import com.labo.anapath.common.audit.AuditableEntity;
import com.labo.anapath.user.User;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Entité représentant un rôle dans le système RBAC du LIS.
 *
 * <p>Un rôle regroupe un ensemble de {@link Permission}s et peut être assigné
 * à plusieurs utilisateurs. Chaque rôle possède un slug unique dérivé de son nom
 * (normalisé en minuscules, sans accents ni caractères spéciaux).</p>
 *
 * <p>Le champ {@code isAssignable} permet de distinguer les rôles système
 * (non assignables manuellement) des rôles métier.</p>
 *
 * <p>La suppression est logique (soft-delete via {@code deleted_at}).</p>
 */
@Entity
@Table(name = "roles")
@SQLDelete(sql = "UPDATE roles SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class Role extends AuditableEntity {

    /** Nom lisible du rôle (ex. : "Administrateur"). */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Slug unique du rôle, généré automatiquement depuis le nom.
     * Utilisé comme identifiant technique stable.
     */
    @Column(name = "slug", nullable = false, unique = true)
    private String slug;

    /** Description optionnelle du rôle et de ses responsabilités. */
    @Column(name = "description")
    private String description;

    /**
     * Indique si ce rôle peut être assigné manuellement à un utilisateur.
     * Les rôles système ({@code false}) sont réservés à un usage interne.
     */
    @Column(name = "is_assignable")
    private Boolean isAssignable = false;

    /**
     * Rôle structurant, dont la suppression est refusée.
     *
     * <p>Un indicateur plutôt qu'une liste de noms dans le code : un rôle
     * renommé garde sa protection, et un laboratoire qui juge un autre rôle
     * tout aussi structurant peut le protéger sans qu'on publie une version.
     * La protection ne porte que sur la suppression — renommer un rôle et
     * changer ses permissions reste le travail normal d'un administrateur.</p>
     */
    @Column(name = "is_protected", nullable = false)
    private Boolean isProtected = false;

    /**
     * Liste des permissions accordées par ce rôle.
     * Chargement paresseux pour éviter les jointures inutiles.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private List<Permission> permissions = new ArrayList<>();

    /**
     * Liste des utilisateurs possédant ce rôle.
     * Relation inverse de {@code User#roles}, chargement paresseux.
     */
    @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
    private List<User> users = new ArrayList<>();
}

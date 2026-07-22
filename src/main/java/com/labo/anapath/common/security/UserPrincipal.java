package com.labo.anapath.common.security;

import com.labo.anapath.user.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Représentation interne de l'utilisateur authentifié dans le contexte de sécurité Spring.
 * <p>
 * Implémente {@link UserDetails} pour s'intégrer avec Spring Security et expose
 * des champs supplémentaires propres au domaine : UUID utilisateur, UUID de branche
 * et statut actif. Les autorités correspondent aux slugs de permissions RBAC.
 * </p>
 * <p>
 * Cette classe est immutable après construction et est placée dans le
 * {@link org.springframework.security.core.context.SecurityContextHolder} par le
 * filtre {@link JwtAuthenticationFilter}.
 * </p>
 */
@Getter
public class UserPrincipal implements UserDetails {

    /** Identifiant unique de l'utilisateur. */
    private final UUID id;

    /** Adresse email, utilisée comme identifiant de connexion ({@code username}). */
    private final String email;

    /** Mot de passe haché (BCrypt), utilisé lors de l'authentification par identifiants. */
    private final String password;

    /** Identifiant de la branche à laquelle l'utilisateur appartient. */
    private final UUID branchId;

    /** Indique si le compte est actif — retourné par {@link #isEnabled()}. */
    private final boolean active;

    /** Slugs de permissions RBAC sous forme de {@link GrantedAuthority}. */
    private final Collection<GrantedAuthority> authorities;

    /**
     * Construit un {@link UserPrincipal} à partir de ses champs constitutifs.
     *
     * @param id          UUID de l'utilisateur
     * @param email       adresse email
     * @param password    mot de passe haché
     * @param branchId    UUID de la branche
     * @param active      statut actif du compte
     * @param authorities collection des autorités (slugs de permissions)
     */
    public UserPrincipal(UUID id, String email, String password, UUID branchId,
                         boolean active, Collection<GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.branchId = branchId;
        this.active = active;
        this.authorities = authorities;
    }

    /**
     * Fabrique un {@link UserPrincipal} depuis une entité {@link User} et la liste
     * de ses slugs de permissions RBAC.
     *
     * @param user        entité utilisateur chargée depuis la base de données
     * @param permissions liste de slugs de permissions issues des rôles de l'utilisateur
     * @return un {@link UserPrincipal} prêt à être injecté dans le contexte de sécurité
     */
    public static UserPrincipal create(User user, List<String> permissions) {
        List<GrantedAuthority> grantedAuthorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getBranchId(),
                user.isActive(),
                grantedAuthorities
        );
    }

    /**
     * Retourne une copie de ce principal en substituant la branche active.
     * <p>
     * Utilisé par {@link BranchContextFilter} pour remplacer la branche « d'attache »
     * issue du JWT par la branche <b>sélectionnée</b> et validée (en-tête
     * {@code X-Branch-Id}), de sorte que tous les {@code principal.getBranchId()} des
     * contrôleurs/services isolent la donnée sur la branche choisie par l'utilisateur.
     * </p>
     *
     * @param newBranchId UUID de la branche sélectionnée
     * @return un nouveau {@link UserPrincipal} identique mais avec la branche substituée
     */
    public UserPrincipal withBranchId(UUID newBranchId) {
        return new UserPrincipal(id, email, password, newBranchId, active, authorities);
    }

    /** {@inheritDoc} */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /** {@inheritDoc} */
    @Override
    public String getPassword() {
        return password;
    }

    /**
     * Retourne l'email comme identifiant principal de l'utilisateur (username Spring Security).
     *
     * @return adresse email de l'utilisateur
     */
    @Override
    public String getUsername() {
        return email;
    }

    /**
     * Les comptes ne sont pas gérés avec une date d'expiration dans cette version.
     *
     * @return toujours {@code true}
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * Le verrouillage de compte n'est pas géré dans cette version.
     *
     * @return toujours {@code true}
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * Les credentials n'ont pas de date d'expiration dans cette version.
     *
     * @return toujours {@code true}
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * Indique si le compte est activé. Un compte inactif est rejeté lors de l'authentification.
     *
     * @return {@code true} si le compte est actif
     */
    @Override
    public boolean isEnabled() {
        return active;
    }
}

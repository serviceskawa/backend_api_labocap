package com.labo.anapath.testsupport;

import com.labo.anapath.common.security.CustomUserDetailsService;
import com.labo.anapath.common.security.JwtTokenProvider;
import com.labo.anapath.common.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Émet un jeton d'accès sans passer par {@code POST /auth/login}.
 *
 * <p>La connexion exige un code à usage unique pour tout le monde, et le
 * limiteur n'accepte que cinq tentatives par minute. Les tests qui éprouvent
 * un autre module n'ont pas à rejouer ce parcours : ils demandent ici le jeton
 * que le challenge aurait rendu, signé par le même fournisseur, porteur des
 * mêmes permissions. Le parcours lui-même est éprouvé par
 * {@code AuthIntegrationTest} et {@code TwoFaIntegrationTest}.</p>
 *
 * <p>Le jeton ne suffit pas : chaque requête doit nommer une agence
 * ({@code X-Branch-Id}, posé par {@link EnTeteDAgence}) à laquelle la personne
 * a accès. Les tests créent leurs comptes avec une agence d'attache mais sans
 * ligne dans {@code branch_user} : on la pose ici — si l'agence existe ; les
 * tests d'authentification en inventent une, et n'appellent que /auth.</p>
 *
 * <p>Composant de test : il vit sous {@code src/test}, donc n'existe que dans
 * le contexte des tests.</p>
 */
@Component
@RequiredArgsConstructor
public class Jetons {

    private final CustomUserDetailsService utilisateurs;
    private final JwtTokenProvider jwt;
    private final JdbcTemplate jdbc;

    public String pour(String email) {
        UserPrincipal personne = (UserPrincipal) utilisateurs.loadUserByUsername(email);
        if (personne.getBranchId() != null) {
            jdbc.update("""
                    INSERT INTO branch_user (user_id, branch_id, is_default)
                    SELECT ?, ?, TRUE
                    WHERE EXISTS (SELECT 1 FROM branches WHERE id = ?)
                      AND NOT EXISTS (
                        SELECT 1 FROM branch_user WHERE user_id = ? AND branch_id = ?)
                    """, personne.getId(), personne.getBranchId(), personne.getBranchId(),
                    personne.getId(), personne.getBranchId());
        }
        return jwt.generateToken(personne);
    }
}

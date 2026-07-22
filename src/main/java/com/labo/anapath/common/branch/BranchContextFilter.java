package com.labo.anapath.common.branch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labo.anapath.branch.BranchRepository;
import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtre imposant la sélection d'une branche (agence/site) active — portage du
 * middleware {@code BranchRequired} de l'app Laravel dans une architecture REST stateless.
 * <p>
 * Là où Laravel lit {@code selected_branch_id} en session et le revalide à chaque
 * requête contre la table {@code branch_user}, on lit ici l'en-tête {@code X-Branch-Id}
 * envoyé par le front et on applique <b>exactement la même règle d'accès</b> :
 * l'utilisateur doit avoir une ligne pivot {@code branch_user} avec {@code is_default = true}
 * et non supprimée (voir {@link BranchRepository#hasBranchAccess}).
 * </p>
 * <p>
 * Quand l'accès est accordé, la branche « d'attache » du JWT portée par le
 * {@link UserPrincipal} est <b>remplacée</b> par la branche sélectionnée : ainsi tous
 * les {@code principal.getBranchId()} des contrôleurs/services isolent la donnée sur la
 * branche choisie (analogue du global scope Eloquent). La branche est aussi déposée dans
 * {@link BranchContext} pour l'estampage automatique à la création.
 * </p>
 * <p>
 * OSIV étant désactivé ({@code spring.jpa.open-in-view=false}), l'isolation ne peut pas
 * s'appuyer sur un filtre Hibernate activé par requête (chaque transaction ouvre sa
 * propre session) : la source de branche portée par le principal garantit le cloisonnement
 * de façon robuste, indépendamment du cycle de vie des sessions JPA.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BranchContextFilter extends OncePerRequestFilter {

    /** En-tête HTTP portant l'identifiant de la branche active choisie côté front. */
    public static final String BRANCH_HEADER = "X-Branch-Id";

    private final BranchRepository branchRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            // Pas d'utilisateur authentifié (ex. endpoints publics) ou route exemptée
            // (flux d'authentification / sélection de branche) : aucune branche requise.
            // Les requêtes non authentifiées sur des routes protégées seront rejetées
            // (401) plus loin par la chaîne d'autorisation Spring Security.
            if (isExempt(request)
                    || authentication == null
                    || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
                filterChain.doFilter(request, response);
                return;
            }

            String header = request.getHeader(BRANCH_HEADER);
            if (!StringUtils.hasText(header)) {
                writeError(response, HttpStatus.PRECONDITION_REQUIRED,
                        "Vous devez sélectionner une branche pour continuer.");
                return;
            }

            UUID branchId;
            try {
                branchId = UUID.fromString(header.trim());
            } catch (IllegalArgumentException ex) {
                writeError(response, HttpStatus.PRECONDITION_REQUIRED,
                        "Branche invalide. Veuillez en sélectionner une autre.");
                return;
            }

            if (!branchRepository.hasBranchAccess(principal.getId(), branchId)) {
                // 428 (comme la branche manquante) : le front réagit à un seul statut
                // « re-sélection de branche requise », sans le confondre avec un 403 de permission.
                writeError(response, HttpStatus.PRECONDITION_REQUIRED,
                        "Votre accès à cette branche a été révoqué. Veuillez en sélectionner une autre.");
                return;
            }

            // Substitution de la branche active : le principal isole désormais la donnée
            // sur la branche sélectionnée (et non la branche d'attache du JWT).
            UserPrincipal branchScoped = principal.withBranchId(branchId);
            UsernamePasswordAuthenticationToken newAuth = new UsernamePasswordAuthenticationToken(
                    branchScoped, authentication.getCredentials(), branchScoped.getAuthorities());
            newAuth.setDetails(authentication.getDetails());
            SecurityContextHolder.getContext().setAuthentication(newAuth);

            BranchContext.set(branchId);

            filterChain.doFilter(request, response);
        } finally {
            BranchContext.clear();
        }
    }

    /**
     * Routes exemptées de la sélection de branche : tout le flux d'authentification
     * ({@code /api/v1/auth/**}, qui inclut la connexion, la 2FA, {@code /me} et
     * {@code /branches}) ainsi que la documentation et la sonde de santé.
     *
     * @param request requête HTTP entrante
     * @return {@code true} si la route ne requiert pas de branche active
     */
    private boolean isExempt(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/auth/")
                // Fichiers servis par chemin UUID opaque : ouverts en navigation
                // directe (nouvel onglet PDF) qui ne peut pas porter l'en-tête
                // X-Branch-Id. L'authentification JWT reste exigée (SecurityConfig).
                || path.startsWith("/api/v1/files/")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/actuator/health");
    }

    /**
     * Écrit une réponse d'erreur JSON conforme à l'enveloppe {@link ApiResponse}.
     *
     * @param response réponse HTTP
     * @param status   statut HTTP à renvoyer
     * @param message  message d'erreur lisible
     * @throws IOException en cas d'erreur d'écriture
     */
    private void writeError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(message));
    }
}

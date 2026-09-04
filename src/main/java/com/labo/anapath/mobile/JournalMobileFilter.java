package com.labo.anapath.mobile;

import com.labo.anapath.common.security.UserPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Consigne tout acte accompli depuis un appareil enrôlé.
 *
 * <h2>Pourquoi au niveau de la requête</h2>
 *
 * <p>Journaliser appel par appel suppose qu'on y pense à chaque nouveau point
 * d'entrée. C'est précisément ce qui échoue : l'oubli ne casse rien, ne se voit
 * pas, et l'on ne s'en aperçoit que le jour où l'on cherche une trace qui
 * n'existe pas. Posé ici, le journal couvre ce qui existe et ce qui viendra.</p>
 *
 * <h2>Ce qui est retenu</h2>
 *
 * <p>Les requêtes qui modifient — POST, PUT, PATCH, DELETE — venant d'une
 * session d'appareil. Les lectures sont écartées : consulter une fiche n'est pas
 * un acte, et les consigner noierait les vrais sous un volume sans rapport.</p>
 *
 * <p>Les échecs sont retenus comme les réussites. Une remise refusée pour droit
 * manquant est justement ce qu'on veut pouvoir retrouver.</p>
 *
 * <h2>Ce qui ne l'est jamais</h2>
 *
 * <p>Le corps des requêtes : contenus médicaux, signatures manuscrites, codes
 * PIN. Un journal qui les recopierait deviendrait le fichier le plus sensible de
 * la base. Deux chemins sont même écartés entièrement — connexion et pose de
 * PIN — car l'utilisateur n'y est pas encore authentifié et il n'y aurait rien
 * à attribuer.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
// Après le filtre d'authentification : sans principal, il n'y a personne à qui
// attribuer l'acte.
@Order(Integer.MAX_VALUE)
public class JournalMobileFilter extends OncePerRequestFilter {

    private static final Set<String> METHODES_QUI_MODIFIENT =
            Set.of("POST", "PUT", "PATCH", "DELETE");

    private final JournalActionMobileRepository journal;
    private final ProvenanceRequete provenance;

    @Override
    protected void doFilterInternal(HttpServletRequest requete,
                                    HttpServletResponse reponse,
                                    FilterChain chaine)
            throws ServletException, IOException {
        // L'identité est relevée AVANT l'appel : Spring Security vide son
        // contexte en fin de requête, et le lire après ne rendrait parfois rien
        // — un journal qui perd des lignes selon l'ordre des filtres est pire
        // qu'un journal absent, puisqu'on le croit complet.
        UserPrincipal principal = principalCourant();
        UUID appareil = provenance.appareilCourant();

        chaine.doFilter(requete, reponse);

        try {
            consigner(requete, reponse, principal, appareil);
        } catch (Exception e) {
            // Le journal ne doit jamais faire échouer l'acte qu'il observe. Un
            // agent au comptoir n'a pas à se voir refuser une remise parce que
            // la trace n'a pas pu s'écrire — c'est le journal qui manque, pas
            // la remise, et la trace du manque est ce log-ci.
            log.warn("Journal mobile : consignation impossible ({} {})",
                    requete.getMethod(), requete.getRequestURI(), e);
        }
    }

    private void consigner(HttpServletRequest requete, HttpServletResponse reponse,
                           UserPrincipal principal, UUID appareil) {
        if (!METHODES_QUI_MODIFIENT.contains(requete.getMethod())) return;
        // Pas d'appareil : session web, journalisée ailleurs. Pas de principal :
        // connexion ou enrôlement, où il n'y a encore personne à qui attribuer.
        if (appareil == null || principal == null) return;

        journal.save(new JournalActionMobile(
                principal.getId(), appareil, principal.getBranchId(),
                requete.getMethod(), requete.getRequestURI(), reponse.getStatus()));
    }

    private UserPrincipal principalCourant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof UserPrincipal p ? p : null;
    }
}

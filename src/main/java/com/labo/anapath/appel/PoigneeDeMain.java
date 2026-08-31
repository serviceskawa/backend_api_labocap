package com.labo.anapath.appel;

import com.labo.anapath.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * Contrôle l'identité avant d'ouvrir la liaison.
 *
 * <h2>Pourquoi ici et non dans un filtre</h2>
 *
 * <p>Une WebSocket ne s'authentifie qu'une fois, à la poignée de main : ensuite
 * les trames circulent sans en-têtes. Laisser passer une liaison anonyme en
 * comptant vérifier les messages plus tard reviendrait à ouvrir le tuyau
 * d'abord et à se demander ensuite à qui il appartient.</p>
 *
 * <h2>Le jeton voyage en en-tête, jamais en paramètre d'URL</h2>
 *
 * <p>Un jeton dans l'URL se retrouve dans les journaux de nginx, dans ceux du
 * navigateur, et dans le référent envoyé au site suivant. En en-tête il ne se
 * consigne nulle part.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PoigneeDeMain implements HandshakeInterceptor {

    /** Clé sous laquelle l'identité voyage jusqu'au gestionnaire. */
    public static final String UTILISATEUR = "utilisateur";
    public static final String BRANCHE = "branche";

    private final JwtTokenProvider jetons;

    @Override
    public boolean beforeHandshake(ServerHttpRequest requete, ServerHttpResponse reponse,
                                   WebSocketHandler gestionnaire, Map<String, Object> attributs) {
        String entete = requete.getHeaders().getFirst("Authorization");
        if (entete == null || !entete.startsWith("Bearer ")) {
            return refuser(reponse, "aucun jeton");
        }
        String jeton = entete.substring(7);
        if (!jetons.validateToken(jeton)) {
            return refuser(reponse, "jeton invalide");
        }
        UUID utilisateur = jetons.extractUserId(jeton);
        if (utilisateur == null) {
            return refuser(reponse, "jeton sans utilisateur");
        }

        attributs.put(UTILISATEUR, utilisateur);
        attributs.put(BRANCHE, jetons.extractBranchId(jeton));
        return true;
    }

    /**
     * Refuse, et le dit dans le code HTTP.
     *
     * <p>Rendre {@code false} sans fixer de statut laisse Spring interrompre la
     * négociation en conservant le {@code 200} par défaut : un refus
     * d'authentification qui se présente comme un succès. L'appareil voit une
     * liaison qui « réussit » puis se ferme aussitôt, et rien — ni dans ses
     * journaux ni dans une requête de contrôle — ne dit que le jeton était en
     * cause.</p>
     */
    private boolean refuser(ServerHttpResponse reponse, String raison) {
        reponse.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
        log.debug("Poignée de main refusée : {}", raison);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest requete, ServerHttpResponse reponse,
                               WebSocketHandler gestionnaire, Exception erreur) {
        // Rien : la liaison est établie ou elle ne l'est pas.
    }
}

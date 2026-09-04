package com.labo.anapath.mobile;

import com.labo.anapath.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * D'où vient la requête en cours : d'un appareil enrôlé, ou d'un poste de travail ?
 *
 * <h2>Pourquoi cette distinction existe</h2>
 *
 * <p>Le web doit continuer de fonctionner tel quel — on ne va pas demander une
 * signature cryptographique à un pathologiste devant son écran, il n'a pas de
 * clé. Mais lorsqu'une session vient d'un téléphone enrôlé, on dispose d'une
 * garantie bien plus forte, et il serait absurde de ne pas l'exiger : sans cela,
 * il suffirait à l'application d'omettre la preuve pour retomber au niveau du
 * web, et tout le dispositif ne vaudrait que par la bonne volonté du client.</p>
 *
 * <h2>Pourquoi lire le jeton plutôt que le principal</h2>
 *
 * <p>Le filtre JWT reconstruit le principal depuis la base à chaque requête et
 * n'y reporte pas les revendications du jeton. Plutôt que de modifier ce filtre
 * — pièce centrale par laquelle passe toute l'authentification —, on relit ici
 * la seule revendication utile. Elle est authentifiée par la signature du jeton,
 * déjà vérifiée en amont : un client ne peut pas se l'inventer.</p>
 */
@Component
@RequiredArgsConstructor
public class ProvenanceRequete {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * @return l'appareil enrôlé d'où provient la session, ou {@code null} pour
     *         une session web — ou hors contexte HTTP, par exemple une tâche
     *         planifiée, auquel cas la contrainte ne s'applique pas.
     */
    public UUID appareilCourant() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributs)) {
            return null;
        }
        String entete = attributs.getRequest().getHeader("Authorization");
        if (entete == null || !entete.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.extractDeviceId(entete.substring(7));
    }
}

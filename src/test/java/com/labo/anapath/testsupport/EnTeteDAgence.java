package com.labo.anapath.testsupport;

import com.labo.anapath.common.branch.BranchContextFilter;
import com.labo.anapath.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Pose {@code X-Branch-Id} sur chaque requête de {@code TestRestTemplate} qui
 * porte un jeton, à partir de l'agence inscrite dans ce jeton.
 *
 * <p>Le front envoie cet en-tête à chaque appel une fois l'agence choisie ;
 * sans lui, le serveur répond 428. Les tests d'intégration ont été écrits
 * avant cette exigence et ne connaissent que le jeton : plutôt que de
 * retoucher chacune de leurs requêtes, on fait ce que fait le front, ici, une
 * fois. Un test qui pose lui-même l'en-tête garde le sien.</p>
 */
@Component
@RequiredArgsConstructor
public class EnTeteDAgence implements RestTemplateCustomizer {

    private final JwtTokenProvider jwt;

    @Override
    public void customize(org.springframework.web.client.RestTemplate restTemplate) {
        restTemplate.getInterceptors().add((request, body, execution) -> {
            String autorisation = request.getHeaders().getFirst("Authorization");
            if (autorisation != null && autorisation.startsWith("Bearer ")
                    && !request.getHeaders().containsKey(BranchContextFilter.BRANCH_HEADER)) {
                try {
                    UUID agence = jwt.extractBranchId(autorisation.substring(7));
                    if (agence != null) {
                        request.getHeaders().set(BranchContextFilter.BRANCH_HEADER, agence.toString());
                    }
                } catch (RuntimeException jetonIllisible) {
                    // Jeton forgé ou périmé par le test : il veut voir le serveur le refuser.
                }
            }
            return execution.execute(request, body);
        });
    }
}

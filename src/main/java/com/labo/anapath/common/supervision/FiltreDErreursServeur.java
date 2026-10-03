package com.labo.anapath.common.supervision;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Compte les réponses 5xx, pour l'alerte « plus de dix erreurs serveur en cinq
 * minutes » de {@link SurveillanceDesSeuils}.
 *
 * <p>Filtre servlet ordinaire, placé le plus à l'extérieur possible : il
 * enveloppe la chaîne de sécurité et les contrôleurs, et lit le statut une fois
 * que tout le monde a répondu. Une exception qui s'échappe de la chaîne est
 * comptée aussi — le conteneur en fera un 500 que personne d'autre ne verrait
 * passer.</p>
 */
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltreDErreursServeur extends OncePerRequestFilter {

    private final CompteurDAlertes compteur;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException e) {
            compteur.erreurServeur();
            throw e;
        }
        if (response.getStatus() >= 500) {
            compteur.erreurServeur();
        }
    }
}

package com.labo.anapath.common.module;

import com.labo.anapath.common.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Ferme les routes propres à la biologie quand le module n'est pas activé.
 *
 * <p>Répond 404 plutôt que 403 : pour un laboratoire sans biologie, ces routes
 * n'existent pas, quelles que soient les permissions de l'utilisateur. Les routes
 * couvertes sont déclarées dans {@link ModulesWebConfig}.
 */
@Component
@RequiredArgsConstructor
public class BiologyModuleInterceptor implements HandlerInterceptor {

    private final ModulesProperties modules;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!modules.isBiology()) {
            throw new ResourceNotFoundException("Le module Biologie n'est pas activé sur ce laboratoire.");
        }
        return true;
    }
}

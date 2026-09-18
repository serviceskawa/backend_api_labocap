package com.labo.anapath.common.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Filtre de limitation de débit (rate limiting) sur les routes ouvertes sans jeton.
 * <p>
 * Deux routes s'atteignent sans être authentifié, et chacune récompense qui la
 * martèle : {@code POST /api/v1/auth/login} rend un mot de passe, et
 * {@code GET /api/v1/public/invoices/{token}} rend une facture. Elles sont donc
 * plafonnées par IP — 5 tentatives par minute pour la première, 30 pour la
 * seconde, un client légitime n'ouvrant son lien qu'une poignée de fois.
 * </p>
 * <p>
 * Répond HTTP 429 en cas de dépassement.
 * </p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /** Préfixe des liens publics de téléchargement de facture. */
    private static final String CHEMIN_FACTURE_PUBLIQUE = "/api/v1/public/invoices/";

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> bucketsFacture = new ConcurrentHashMap<>();

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1))))
                .build();
    }

    private Bucket newBucketFacture() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(30, Refill.intervally(30, Duration.ofMinutes(1))))
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if ("/api/v1/auth/login".equals(request.getRequestURI()) && "POST".equals(request.getMethod())) {
            String ip = getClientIp(request);
            Bucket bucket = buckets.computeIfAbsent(ip, k -> newBucket());
            if (!bucket.tryConsume(1)) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"Trop de tentatives. Réessayez dans 1 minute.\"}");
                return;
            }
        } else if (request.getRequestURI().startsWith(CHEMIN_FACTURE_PUBLIQUE)
                && "GET".equals(request.getMethod())) {
            String ip = getClientIp(request);
            Bucket bucket = bucketsFacture.computeIfAbsent(ip, k -> newBucketFacture());
            if (!bucket.tryConsume(1)) {
                // Le lien s'ouvre dans un navigateur : un JSON d'erreur y serait illisible.
                response.setStatus(429);
                response.setContentType("text/html;charset=UTF-8");
                response.getWriter().write(
                        "<!doctype html><meta charset=\"utf-8\"><p>Trop de demandes. Réessayez dans une minute.</p>");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

package com.labo.anapath.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Composant central de gestion des tokens JWT.
 * <p>
 * Génère et valide trois types de tokens distincts, différenciés par le claim {@code type} :
 * <ul>
 *   <li><b>Access token</b> (pas de claim {@code type}) — durée 24 h, embarque les slugs de
 *       permissions RBAC et le {@code branchId} ; utilisé pour accéder aux ressources protégées.</li>
 *   <li><b>Refresh token</b> ({@code type = "refresh"}) — durée 7 jours ; utilisé uniquement
 *       pour obtenir un nouvel access token via {@code /api/v1/auth/refresh}.</li>
 *   <li><b>Token temporaire 2FA</b> ({@code type = "2fa-challenge"}) — durée 5 min ; émis
 *       lors d'un login réussi si la 2FA est activée, permettant au client de soumettre
 *       son code TOTP.</li>
 * </ul>
 * Tous les tokens sont signés HMAC-SHA avec la clé configurée dans {@link JwtProperties}.
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    /**
     * Durée de validité du token temporaire de challenge 2FA (5 minutes).
     * <p>
     * Exposée pour que le client puisse afficher un décompte et verrouiller l'écran
     * de connexion tant que le challenge en cours n'a pas expiré (analogue de la
     * session {@code user_2fa} de Laravel).
     * </p>
     */
    public static final long TEMP_TOKEN_VALIDITY_MS = 300_000L;

    /**
     * Construit la clé HMAC-SHA à partir du secret configuré.
     *
     * @return clé de signature prête à l'emploi pour JJWT
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Génère un token d'accès complet (access token) pour un utilisateur authentifié.
     * <p>
     * Le token embarque : l'UUID utilisateur (subject), le {@code branchId},
     * les slugs de permissions RBAC (claim {@code permissions}), un JTI unique et
     * une durée de validité configurée par {@link JwtProperties#getExpirationMs()}.
     * </p>
     *
     * @param userPrincipal principal de l'utilisateur authentifié
     * @return token JWT signé sous forme de chaîne compacte
     */
    public String generateToken(UserPrincipal userPrincipal) {
        return generateToken(userPrincipal, null);
    }

    /**
     * Variante portant l'appareil enrôlé d'où la session a été ouverte.
     *
     * <p>La revendication {@code deviceId} n'est posée que par l'application
     * mobile. Elle permet de distinguer une session de téléphone d'une session
     * de poste de travail, et donc d'exiger une signature d'appareil sur les
     * actes engageants <strong>sans rien changer pour le web</strong>, qui doit
     * continuer de fonctionner tel quel.</p>
     *
     * <p>Elle ne confère aucun droit : c'est une provenance, pas une
     * autorisation. Les permissions restent rechargées depuis la base à chaque
     * requête, et un jeton forgé échouerait à la vérification de signature du
     * JWT lui-même.</p>
     *
     * @param deviceId appareil d'origine, ou {@code null} pour une session web
     */
    public String generateToken(UserPrincipal userPrincipal, UUID deviceId) {
        Date now = new Date();
        // La présence d'un appareil distingue les deux durées : une session web
        // se ferme après quinze minutes sans activité, un téléphone non. Il se
        // verrouille de lui-même et redemande son code PIN selon le métier.
        Date expiry = new Date(now.getTime() + (deviceId != null
                ? jwtProperties.getMobileExpirationMs()
                : jwtProperties.getExpirationMs()));

        // Permissions are NOT embedded in the token — the filter reloads them from DB
        // (JwtAuthenticationFilter.loadUserById). Embedding 300+ slugs would bloat the JWT.
        var builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userPrincipal.getId().toString())
                .claim("branchId", userPrincipal.getBranchId().toString());
        if (deviceId != null) {
            builder.claim("deviceId", deviceId.toString());
        }
        return builder
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Appareil enrôlé d'où provient la session, ou {@code null} pour le web.
     *
     * <p>Sert à exiger une preuve d'appareil là où la provenance mobile permet
     * une garantie plus forte que la simple session ouverte.</p>
     */
    public UUID extractDeviceId(String token) {
        try {
            Object valeur = extractAllClaims(token).get("deviceId");
            return valeur == null ? null : UUID.fromString(valeur.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Génère un token temporaire de challenge 2FA, valide 5 minutes.
     * <p>
     * Ce token (claim {@code type = "2fa-challenge"}) est retourné au client
     * lorsque l'utilisateur a validé ses identifiants mais que la 2FA est activée.
     * Il doit être présenté lors de la validation TOTP sur {@code /api/v1/auth/2fa/challenge}.
     * Le filtre JWT rejette ce type de token pour tout autre endpoint.
     * </p>
     *
     * @param userId UUID de l'utilisateur en cours d'authentification
     * @return token JWT temporaire signé
     */
    public String generateTempToken(UUID userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + TEMP_TOKEN_VALIDITY_MS); // 5 minutes

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("type", "2fa-challenge")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Génère un token de rafraîchissement (refresh token), valide selon la configuration.
     * <p>
     * Ce token (claim {@code type = "refresh"}) ne contient pas les permissions et
     * ne doit être utilisé que sur l'endpoint {@code /api/v1/auth/refresh}.
     * Le filtre JWT rejette ce type de token pour tout autre endpoint.
     * </p>
     *
     * @param userId UUID de l'utilisateur
     * @return token de rafraîchissement JWT signé
     */
    public String generateRefreshToken(UUID userId) {
        return generateRefreshToken(userId, false);
    }

    /**
     * Même chose, en distinguant la fenêtre d'un appareil de celle du web.
     *
     * @param pourAppareil vrai pour un téléphone enrôlé, dont la session ne suit
     *                     pas les quinze minutes du poste de travail
     */
    public String generateRefreshToken(UUID userId, boolean pourAppareil) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + (pourAppareil
                ? jwtProperties.getMobileRefreshExpirationMs()
                : jwtProperties.getRefreshExpirationMs()));

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Extrait l'UUID utilisateur depuis le subject du token.
     *
     * @param token token JWT signé
     * @return UUID de l'utilisateur
     */
    public UUID extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Extrait la liste des slugs de permissions RBAC embarquée dans le token d'accès.
     *
     * @param token token JWT d'accès
     * @return liste des slugs de permissions, ou liste vide si le claim est absent
     */
    @SuppressWarnings("unchecked")
    public List<String> extractPermissions(String token) {
        Claims claims = extractAllClaims(token);
        Object permissions = claims.get("permissions");
        if (permissions instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    /**
     * Extrait l'UUID de la branche depuis le claim {@code branchId} du token.
     *
     * @param token token JWT d'accès
     * @return UUID de la branche, ou {@code null} si le claim est absent
     */
    public UUID extractBranchId(String token) {
        Claims claims = extractAllClaims(token);
        String branchIdStr = (String) claims.get("branchId");
        if (branchIdStr == null) {
            return null;
        }
        return UUID.fromString(branchIdStr);
    }

    /**
     * Extrait le type du token depuis le claim {@code type}.
     *
     * @param token token JWT signé
     * @return valeur du claim {@code type} ({@code "refresh"}, {@code "2fa-challenge"}),
     *         ou {@code null} pour un access token standard ou en cas d'erreur
     */
    public String extractType(String token) {
        try {
            return (String) extractAllClaims(token).get("type");
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Extrait l'identifiant unique (JTI) du token, utilisé par la blacklist après logout.
     *
     * @param token token JWT signé
     * @return valeur du claim {@code jti}
     */
    public String extractJti(String token) {
        return extractAllClaims(token).getId();
    }

    /**
     * Extrait la date d'expiration du token sous forme d'un {@link Instant}.
     *
     * @param token token JWT signé
     * @return instant d'expiration du token
     */
    public Instant extractExpiry(String token) {
        return extractAllClaims(token).getExpiration().toInstant();
    }

    /**
     * Valide un token JWT : vérifie la signature et l'expiration.
     *
     * @param token token JWT à valider
     * @return {@code true} si le token est valide et non expiré, {@code false} sinon
     */
    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return true;
        } catch (JwtException ex) {
            log.warn("Invalid JWT token: {}", ex.getMessage());
            return false;
        } catch (Exception ex) {
            log.error("JWT validation error: {}", ex.getMessage());
            return false;
        }
    }

    /**
     * Parse et retourne l'ensemble des claims d'un token JWT après vérification de la signature.
     *
     * @param token token JWT signé
     * @return claims du payload
     * @throws JwtException si la signature est invalide ou si le token est expiré
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}

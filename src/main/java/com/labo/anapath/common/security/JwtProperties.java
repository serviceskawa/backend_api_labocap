package com.labo.anapath.common.security;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Propriétés de configuration JWT lues depuis {@code application.yml} sous le préfixe {@code app.jwt}.
 * <p>
 * Exemple de configuration :
 * <pre>{@code
 * app:
 *   jwt:
 *     secret: "<clé-hmac-256-bits>"
 *     expiration-ms: 86400000      # 24 heures
 *     refresh-expiration-ms: 604800000  # 7 jours
 * }</pre>
 * </p>
 */
@Component
@ConfigurationProperties(prefix = "app.jwt")
@Getter
@Setter
public class JwtProperties {

    /** Clé secrète HMAC-SHA utilisée pour signer et vérifier les tokens JWT. */
    private String secret;

    /** Durée de validité du token d'accès web, en millisecondes. */
    private long expirationMs;

    /**
     * Durée de la fenêtre de session web, en millisecondes.
     *
     * <p>C'est elle qui décide de la durée d'une session : le navigateur
     * renouvelle seul le jeton d'accès tant que celui-ci vit encore.</p>
     */
    private long refreshExpirationMs;

    /**
     * Durée de validité du jeton d'accès d'un appareil mobile.
     *
     * <p>Séparée de la web à dessein. Un téléphone n'est pas un poste laissé
     * ouvert au comptoir : il se verrouille de lui-même, et l'application
     * redemande le code PIN selon le métier — chaque heure au laboratoire, deux
     * fois par jour au comptoir. Lui imposer la fenêtre du web réclamerait un
     * code toutes les quinze minutes, ce que cette politique refuse
     * explicitement.</p>
     */
    private long mobileExpirationMs = 86_400_000L;

    /** Fenêtre de session d'un appareil mobile. Voir {@link #mobileExpirationMs}. */
    private long mobileRefreshExpirationMs = 604_800_000L;

    @PostConstruct
    public void validate() {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException(
                "La variable d'environnement JWT_SECRET est obligatoire. " +
                "Générez une clé forte avec: openssl rand -base64 32");
        }
        if (secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                "JWT_SECRET doit faire au moins 256 bits (32 octets). " +
                "Générez une clé forte avec: openssl rand -base64 32");
        }
    }
}

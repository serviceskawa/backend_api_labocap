package com.labo.anapath.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.labo.anapath.user.UserResponseDto;

/**
 * DTO de réponse du flux d'authentification.
 * <p>
 * Représente deux états distincts selon que la 2FA est activée ou non :
 * <ul>
 *   <li><b>Connexion directe (2FA désactivée)</b> : {@code expiresIn} et {@code user} sont
 *       renseignés dans le JSON ; les tokens sont transmis via des cookies HttpOnly (non sérialisés).</li>
 *   <li><b>Challenge 2FA requis</b> : seuls {@code requires2fa = true} et {@code tempToken}
 *       sont renseignés ; les autres champs sont {@code null}.</li>
 * </ul>
 * </p>
 *
 * @param accessToken  token d'accès JWT — non sérialisé en JSON, transmis via cookie HttpOnly
 * @param refreshToken token de rafraîchissement JWT — non sérialisé en JSON, transmis via cookie HttpOnly
 * @param expiresIn    durée de validité en secondes — du token d'accès en connexion directe,
 *                     du token temporaire de challenge lorsque {@code requires2fa = true}
 * @param user         informations de l'utilisateur connecté (null si challenge 2FA)
 * @param requires2fa  {@code true} si un challenge TOTP est requis pour finaliser la connexion
 * @param tempToken    token temporaire de challenge 2FA valide 5 min (null si pas de 2FA)
 */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        Long expiresIn,
        UserResponseDto user,
        Boolean requires2fa,
        String tempToken,
        /**
         * Canal par lequel le code est attendu : {@code "APP"} quand
         * l'utilisateur a une application d'authentification, {@code "EMAIL"}
         * sinon.
         *
         * <p>Le client en a besoin pour se formuler correctement : annoncer un
         * « code envoyé à v…@caap.bj » à quelqu'un qui n'a rien reçu le ferait
         * attendre un courriel qui ne viendra pas, puis appeler l'assistance.
         * Les deux canaux restent acceptés à la vérification — ce champ ne dit
         * pas ce qui est exigé, seulement ce qui a été fait.</p>
         */
        String otpCanal
) {
    /** Masqué du JSON — transmis via cookie HttpOnly côté {@link com.labo.anapath.auth.AuthController}. */
    @JsonIgnore
    @Override
    public String accessToken() { return accessToken; }

    /** Masqué du JSON — transmis via cookie HttpOnly côté {@link com.labo.anapath.auth.AuthController}. */
    @JsonIgnore
    @Override
    public String refreshToken() { return refreshToken; }

    /**
     * Constructeur de commodité pour une connexion réussie sans 2FA.
     *
     * @param accessToken  token d'accès JWT
     * @param refreshToken token de rafraîchissement JWT
     * @param expiresIn    durée de validité du token d'accès en secondes
     * @param user         informations de l'utilisateur connecté
     */
    public LoginResponse(String accessToken, String refreshToken, Long expiresIn, UserResponseDto user) {
        this(accessToken, refreshToken, expiresIn, user, null, null, null);
    }

    /**
     * Fabrique une réponse indiquant qu'un challenge 2FA est requis.
     *
     * @param tempToken token temporaire à présenter sur {@code /api/v1/auth/2fa/challenge}
     * @param expiresIn durée de validité restante du challenge, en secondes — permet au
     *                  client d'afficher un décompte et de verrouiller l'écran de connexion
     *                  tant que le code n'a pas expiré
     * @return réponse avec {@code requires2fa = true}, le token temporaire et sa durée de vie
     */
    public static LoginResponse requires2fa(String tempToken, long expiresIn, String otpCanal) {
        return new LoginResponse(null, null, expiresIn, null, true, tempToken, otpCanal);
    }
}

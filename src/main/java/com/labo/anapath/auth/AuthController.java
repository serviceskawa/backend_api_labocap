package com.labo.anapath.auth;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.security.JwtProperties;
import com.labo.anapath.common.security.JwtTokenProvider;
import com.labo.anapath.common.security.UserPrincipal;
import com.labo.anapath.branch.UserBranchResponseDto;
import com.labo.anapath.user.UserResponseDto;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Contrôleur REST gérant l'authentification et les opérations 2FA.
 * <p>
 * Expose les endpoints publics et protégés du module auth sous {@code /api/v1/auth}.
 * Les tokens JWT (access et refresh) sont transmis exclusivement via des cookies
 * {@code HttpOnly; Secure; SameSite=Strict} — jamais dans le corps JSON.
 * </p>
 * <p>Flux d'authentification avec 2FA :</p>
 * <ol>
 *   <li>{@code POST /login} → pose les cookies si 2FA désactivée, sinon retourne
 *       un {@code tempToken} dans le JSON pour le challenge.</li>
 *   <li>{@code POST /2fa/challenge} → valide le code TOTP, pose les cookies définitifs.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String ACCESS_COOKIE  = "access_token";
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String API_PATH        = "/";
    private static final String REFRESH_PATH    = "/api/v1/auth/refresh";

    /**
     * Cookie HttpOnly portant le token temporaire d'un challenge 2FA en cours.
     * <p>
     * Équivalent stateless de la session Laravel « authentifié mais {@code user_2fa}
     * absent » : sa présence signifie qu'un utilisateur a validé ses identifiants et
     * doit saisir le code reçu par e-mail. Le front (proxy Next) s'en sert pour
     * interdire le retour à l'écran de connexion et pour n'autoriser l'écran de
     * saisie du code que dans ce contexte. Il expire de lui-même avec le token (5 min).
     * </p>
     */
    private static final String PENDING_2FA_COOKIE = "pending_2fa";

    private final AuthService authService;
    private final TwoFaService twoFaService;
    private final JwtProperties jwtProperties;

    /**
     * Attribut {@code Secure} des cookies d'authentification.
     * <p>
     * {@code true} par défaut (obligatoire en production HTTPS). À passer à
     * {@code false} UNIQUEMENT sur un environnement servi en HTTP clair (ex.
     * démo sur IP brute), sans quoi le navigateur refuse de stocker les cookies
     * et l'utilisateur est renvoyé sur /login. Ne jamais laisser {@code false}
     * en production.
     * </p>
     */
    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    /**
     * Domaine des cookies d'authentification. Vide par défaut.
     * <p>
     * Sans cet attribut, un cookie est <em>host-only</em> : posé par
     * {@code api.exemple.fr}, il n'est renvoyé qu'à {@code api.exemple.fr}. Le
     * front servi depuis {@code app.exemple.fr} ne le reçoit donc jamais, et sa
     * garde de routes — qui teste la seule présence du cookie — renvoie
     * indéfiniment sur /login alors que l'authentification a réussi.
     * </p><p>
     * Renseigner {@code .exemple.fr} (avec le point initial) pour partager les
     * cookies entre sous-domaines. Laisser vide quand front et API partagent
     * l'hôte — c'est le cas en développement, où {@code localhost:3001} et
     * {@code localhost:8080} se partagent déjà les cookies, les ports n'entrant
     * pas dans leur portée. D'où un défaut invisible hors production.
     * </p><p>
     * ⚠ Jamais un suffixe public seul ({@code .fr}, {@code .bj}) : le
     * navigateur rejetterait le cookie sans le moindre message.
     * </p>
     */
    @Value("${app.cookie.domain:}")
    private String cookieDomain;

    /**
     * Socle commun des six cookies d'authentification — les trois posés
     * ({@code access_token}, {@code refresh_token}, {@code pending_2fa}) et les
     * trois effacés.
     * <p>
     * Le domaine doit figurer <em>aussi</em> sur les cookies d'effacement : un
     * cookie n'est supprimé que si domaine et chemin correspondent exactement à
     * ceux de la pose. Sans quoi la déconnexion laisserait le jeton en place.
     * </p>
     */
    private ResponseCookie.ResponseCookieBuilder cookieBuilder(String name, String value, String path) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(path);
        if (StringUtils.hasText(cookieDomain)) {
            builder.domain(cookieDomain);
        }
        return builder;
    }

    /**
     * Authentifie un utilisateur avec son email et son mot de passe.
     * <p>
     * Si la 2FA est désactivée, pose les cookies {@code access_token} et {@code refresh_token}
     * HttpOnly sur la réponse et retourne uniquement les métadonnées (user, expiresIn).
     * Si la 2FA est activée, retourne un {@code tempToken} dans le JSON pour le challenge.
     * </p>
     *
     * @param request  corps de la requête contenant email et mot de passe
     * @param response réponse HTTP pour poser les cookies
     * @return métadonnées de l'utilisateur ou challenge 2FA
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {
        LoginResponse loginResponse = authService.login(request);
        if (Boolean.TRUE.equals(loginResponse.requires2fa())) {
            // Challenge en cours : le token temporaire est aussi posé en cookie HttpOnly
            // pour que la validation du code ne dépende pas d'un stockage navigateur
            // (onglet fermé, autre onglet…) et pour que le front puisse verrouiller
            // l'écran de connexion tant que le challenge n'a pas expiré.
            writePending2faCookie(response, loginResponse.tempToken());
        } else {
            writeTokenCookies(response, loginResponse);
        }
        return ResponseEntity.ok(ApiResponse.success("Connexion réussie", loginResponse));
    }

    /**
     * Rafraîchit le token d'accès à partir du cookie {@code refresh_token}.
     * <p>
     * Le refresh token est lu depuis le cookie HttpOnly (envoyé automatiquement par le navigateur
     * sur {@code Path=/api/v1/auth/refresh}). Pose de nouveaux cookies en réponse.
     * </p>
     *
     * @param httpRequest requête HTTP pour extraire le cookie refresh
     * @param response    réponse HTTP pour poser les nouveaux cookies
     * @return métadonnées de l'utilisateur mises à jour
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String refreshToken = extractCookieValue(httpRequest, REFRESH_COOKIE);
        if (!StringUtils.hasText(refreshToken)) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Cookie refresh_token absent ou invalide"));
        }
        LoginResponse loginResponse = authService.refresh(refreshToken);
        writeTokenCookies(response, loginResponse);
        return ResponseEntity.ok(ApiResponse.success("Token rafraîchi", loginResponse));
    }

    /**
     * Déconnecte l'utilisateur : blackliste le token courant et efface les cookies.
     *
     * @param httpRequest requête HTTP pour extraire le token (cookie ou header)
     * @param response    réponse HTTP pour effacer les cookies
     * @param principal   utilisateur authentifié
     * @return confirmation de déconnexion
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse response,
            @AuthenticationPrincipal UserPrincipal principal) {
        String token = extractCookieValue(httpRequest, ACCESS_COOKIE);
        if (!StringUtils.hasText(token)) {
            token = extractBearerToken(httpRequest);
        }
        authService.logout(token);
        clearTokenCookies(response);
        clearPending2faCookie(response);
        return ResponseEntity.ok(ApiResponse.success("Déconnexion réussie", null));
    }

    /**
     * Initialise la configuration 2FA pour l'utilisateur connecté.
     * <p>
     * Génère un secret TOTP, le persiste et retourne le QR code (base64 PNG)
     * à scanner avec Google Authenticator. La 2FA n'est pas encore activée
     * tant que {@code /2fa/verify} n'est pas appelé avec succès.
     * </p>
     *
     * @param principal utilisateur authentifié dont on configure la 2FA
     * @return secret TOTP et QR code base64 pour la configuration de l'application TOTP
     */
    @PostMapping("/2fa/setup")
    public ResponseEntity<ApiResponse<TwoFaSetupResponse>> setup2fa(
            @AuthenticationPrincipal UserPrincipal principal) {
        TwoFaSetupResponse response = twoFaService.setup(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("QR code généré", response));
    }

    /**
     * Vérifie le premier code TOTP et active la 2FA sur le compte.
     * <p>
     * Doit être appelé après {@code /2fa/setup} pour confirmer que l'utilisateur
     * a bien scanné le QR code et que son application TOTP fonctionne correctement.
     * </p>
     *
     * @param request   code TOTP à 6 chiffres saisi par l'utilisateur
     * @param principal utilisateur authentifié
     * @return confirmation d'activation
     */
    @PostMapping("/2fa/verify")
    public ResponseEntity<ApiResponse<Void>> verify2fa(
            @Valid @RequestBody TwoFaCodeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        twoFaService.verifyAndEnable(principal.getId(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success("2FA activée avec succès", null));
    }

    /**
     * Désactive la 2FA sur le compte après vérification du code TOTP courant.
     *
     * @param request   code TOTP à 6 chiffres pour confirmer la désactivation
     * @param principal utilisateur authentifié
     * @return confirmation de désactivation
     */
    @PostMapping("/2fa/disable")
    public ResponseEntity<ApiResponse<Void>> disable2fa(
            @Valid @RequestBody TwoFaCodeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        twoFaService.disable(principal.getId(), request.getCode());
        return ResponseEntity.ok(ApiResponse.success("2FA désactivée", null));
    }

    /**
     * Valide le code reçu par e-mail dans le cadre du flux de challenge 2FA et pose les
     * cookies définitifs.
     * <p>
     * Le token temporaire est lu depuis le corps de la requête ou, à défaut, depuis le
     * cookie HttpOnly {@code pending_2fa} posé au login : le client n'a donc jamais besoin
     * de le conserver lui-même. Le cookie de challenge est effacé en cas de succès.
     * </p>
     *
     * @param request     corps contenant le code reçu par e-mail (et éventuellement le token temporaire)
     * @param httpRequest requête HTTP pour lire le cookie de challenge
     * @param response    réponse HTTP pour poser les cookies access + refresh
     * @return métadonnées de l'utilisateur connecté
     */
    @PostMapping("/2fa/challenge")
    public ResponseEntity<ApiResponse<LoginResponse>> challenge2fa(
            @Valid @RequestBody TwoFactorVerifyRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        if (!StringUtils.hasText(request.getTempToken())) {
            request.setTempToken(extractCookieValue(httpRequest, PENDING_2FA_COOKIE));
        }
        LoginResponse loginResponse = authService.challenge(request);
        clearPending2faCookie(response);
        writeTokenCookies(response, loginResponse);
        return ResponseEntity.ok(ApiResponse.success("Authentification 2FA réussie", loginResponse));
    }

    /**
     * Retourne le profil de l'utilisateur actuellement authentifié.
     * <p>
     * L'identifiant et la succursale sont extraits directement du JWT via
     * {@link UserPrincipal}, garantissant l'isolation multi-tenant sans paramètre supplémentaire.
     * </p>
     *
     * @param principal utilisateur authentifié extrait du JWT
     * @return profil complet de l'utilisateur connecté
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponseDto>> me(
            @AuthenticationPrincipal UserPrincipal principal) {
        UserResponseDto userDto = authService.me(principal.getId(), principal.getBranchId());
        return ResponseEntity.ok(ApiResponse.success("Profil utilisateur", userDto));
    }

    /**
     * Liste les branches (agences/sites) accessibles par l'utilisateur connecté.
     * <p>
     * Alimente l'écran de sélection de branche du front (analogue de la page
     * {@code select-branch} de Laravel). Endpoint authentifié mais exempté de
     * l'exigence de branche active — il sert précisément à la choisir.
     * </p>
     *
     * @param principal utilisateur authentifié extrait du JWT
     * @return liste des branches accessibles, branche(s) par défaut en tête
     */
    @GetMapping("/branches")
    public ResponseEntity<ApiResponse<List<UserBranchResponseDto>>> branches(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<UserBranchResponseDto> userBranches = authService.getUserBranches(principal.getId());
        return ResponseEntity.ok(ApiResponse.success("Branches accessibles", userBranches));
    }

    /**
     * Initie la réinitialisation du mot de passe en générant un token UUID.
     * <p>
     * Comme aucun MailService n'est disponible, le token est retourné directement
     * dans la réponse JSON à des fins de développement et de test.
     * </p>
     *
     * @param request corps contenant l'adresse e-mail du compte
     * @return map JSON contenant le token de réinitialisation (clé {@code "token"})
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Map<String, String>>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        Map<String, String> result = authService.forgotPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Token de réinitialisation généré", result));
    }

    /**
     * Réinitialise le mot de passe à partir d'un token valide et non expiré.
     *
     * @param request corps contenant le token, le nouveau mot de passe et sa confirmation
     * @return confirmation de la réinitialisation
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(ApiResponse.success("Mot de passe réinitialisé avec succès", null));
    }

    /**
     * Génère un code OTP à 6 chiffres et l'envoie par email à l'utilisateur.
     *
     * @param request corps contenant l'adresse e-mail du compte
     * @return confirmation d'envoi
     */
    @PostMapping("/resend-2fa")
    public ResponseEntity<ApiResponse<Void>> resend2FA(
            @Valid @RequestBody Resend2FARequest request) {
        authService.resend2FA(request);
        return ResponseEntity.ok(ApiResponse.success("Code OTP envoyé par email", null));
    }

    // -------------------------------------------------------------------------
    // Helpers cookies
    // -------------------------------------------------------------------------

    /**
     * Pose les cookies {@code access_token} et {@code refresh_token} HttpOnly sur la réponse.
     */
    private void writeTokenCookies(HttpServletResponse response, LoginResponse loginResponse) {
        ResponseCookie accessCookie = cookieBuilder(ACCESS_COOKIE, loginResponse.accessToken(), API_PATH)
                .maxAge(Duration.ofMillis(jwtProperties.getExpirationMs()))
                .build();

        ResponseCookie refreshCookie = cookieBuilder(REFRESH_COOKIE, loginResponse.refreshToken(), REFRESH_PATH)
                .maxAge(Duration.ofMillis(jwtProperties.getRefreshExpirationMs()))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    /**
     * Pose le cookie HttpOnly {@code pending_2fa} portant le token temporaire de challenge.
     * <p>
     * Sa durée de vie est exactement celle du token ({@link JwtTokenProvider#TEMP_TOKEN_VALIDITY_MS}) :
     * le verrouillage de l'écran de connexion côté front se lève donc tout seul à l'expiration
     * du code, sans action de l'utilisateur.
     * </p>
     */
    private void writePending2faCookie(HttpServletResponse response, String tempToken) {
        ResponseCookie pendingCookie = cookieBuilder(PENDING_2FA_COOKIE, tempToken, API_PATH)
                .maxAge(Duration.ofMillis(JwtTokenProvider.TEMP_TOKEN_VALIDITY_MS))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, pendingCookie.toString());
    }

    /** Efface le cookie de challenge 2FA (challenge abouti ou déconnexion). */
    private void clearPending2faCookie(HttpServletResponse response) {
        ResponseCookie clearPending = cookieBuilder(PENDING_2FA_COOKIE, "", API_PATH)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, clearPending.toString());
    }

    /**
     * Efface les cookies d'authentification en posant des cookies expirés (maxAge=0).
     */
    private void clearTokenCookies(HttpServletResponse response) {
        ResponseCookie clearAccess = cookieBuilder(ACCESS_COOKIE, "", API_PATH)
                .maxAge(0)
                .build();

        ResponseCookie clearRefresh = cookieBuilder(REFRESH_COOKIE, "", REFRESH_PATH)
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, clearAccess.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, clearRefresh.toString());
    }

    /** Lit la valeur d'un cookie nommé depuis la requête, ou {@code null} si absent. */
    private String extractCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    /** Extrait le token JWT brut depuis l'en-tête {@code Authorization: Bearer <token>}. */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}

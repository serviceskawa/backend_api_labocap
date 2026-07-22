package com.labo.anapath.auth;

import com.labo.anapath.branch.UserBranchResponseDto;
import com.labo.anapath.user.UserResponseDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contrat du service d'authentification JWT.
 * <p>
 * Définit les opérations du flux d'authentification complet, incluant la gestion
 * du challenge 2FA (Google Authenticator TOTP).
 * </p>
 */
public interface AuthService {

    /**
     * Authentifie un utilisateur avec ses identifiants.
     * <p>
     * Si la 2FA est activée sur le compte, retourne un token temporaire de challenge
     * ({@code requires2fa = true}) valide 5 minutes. Sinon, retourne les tokens
     * d'accès et de rafraîchissement définitifs.
     * </p>
     *
     * @param request identifiants de connexion (email + mot de passe)
     * @return réponse avec les tokens ou le challenge 2FA
     */
    LoginResponse login(LoginRequest request);

    /**
     * Renouvelle le token d'accès à partir d'un refresh token valide et non révoqué.
     *
     * @param refreshToken le refresh token JWT brut (extrait du cookie par le contrôleur)
     * @return nouveaux tokens d'accès et de rafraîchissement
     */
    LoginResponse refresh(String refreshToken);

    /**
     * Révoque le token JWT fourni en l'ajoutant à la blacklist.
     * <p>
     * Met également à jour l'état de connexion de l'utilisateur en base ({@code is_connect = false}).
     * Si le token est absent ou invalide, l'opération est ignorée silencieusement.
     * </p>
     *
     * @param token token JWT à révoquer (sans le préfixe "Bearer ")
     */
    void logout(String token);

    /**
     * Valide le code TOTP soumis dans le cadre du challenge 2FA et émet les tokens définitifs.
     *
     * @param request requête contenant le token temporaire de challenge et le code TOTP à 6 chiffres
     * @return tokens d'accès et de rafraîchissement définitifs
     */
    LoginResponse challenge(TwoFactorVerifyRequest request);

    /**
     * Retourne le profil de l'utilisateur authentifié identifié par son UUID et sa succursale.
     *
     * @param userId   UUID de l'utilisateur extrait du JWT
     * @param branchId UUID de la succursale extrait du JWT
     * @return DTO du profil utilisateur
     */
    UserResponseDto me(UUID userId, UUID branchId);

    /**
     * Génère un token de réinitialisation de mot de passe et le stocke en base.
     * <p>
     * Pour les besoins du développement (pas de MailService), le token est retourné
     * directement dans la réponse.
     * </p>
     *
     * @param request requête contenant l'adresse e-mail du compte
     * @return map contenant le token généré (clé {@code "token"})
     */
    Map<String, String> forgotPassword(ForgotPasswordRequest request);

    /**
     * Réinitialise le mot de passe d'un utilisateur à partir d'un token valide et non expiré.
     *
     * @param request requête contenant le token, le nouveau mot de passe et sa confirmation
     * @throws com.labo.anapath.common.exception.BusinessException      si les mots de passe ne correspondent pas
     * @throws com.labo.anapath.common.exception.UnauthorizedException  si le token est invalide ou expiré
     */
    void resetPassword(ResetPasswordRequest request);

    /**
     * Génère un code OTP à 6 chiffres, le stocke haché dans la table {@code two_fas}
     * et l'envoie par email à l'utilisateur. Opération silencieuse si l'email est introuvable.
     *
     * @param request requête contenant l'adresse e-mail du compte
     */
    void resend2FA(Resend2FARequest request);

    /**
     * Liste les branches (agences/sites) accessibles par l'utilisateur connecté, pour
     * alimenter l'écran de sélection de branche — analogue du chargement des branches
     * depuis {@code branch_user} dans {@code TFAuthController::selectBranch} de Laravel.
     * <p>
     * Les branches marquées par défaut apparaissent en tête ; les affectations et
     * branches supprimées ({@code deleted_at}) sont exclues.
     * </p>
     *
     * @param userId UUID de l'utilisateur connecté
     * @return liste des branches accessibles, branche(s) par défaut d'abord
     */
    List<UserBranchResponseDto> getUserBranches(UUID userId);
}

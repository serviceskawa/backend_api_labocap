package com.labo.anapath.auth;

import com.labo.anapath.branch.BranchRepository;
import com.labo.anapath.branch.UserBranchResponseDto;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.InvalidCodeException;
import com.labo.anapath.common.exception.UnauthorizedException;
import com.labo.anapath.common.security.CustomUserDetailsService;
import com.labo.anapath.common.security.JwtProperties;
import com.labo.anapath.common.security.JwtTokenProvider;
import com.labo.anapath.common.security.TokenBlacklistService;
import com.labo.anapath.common.security.UserPrincipal;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserMapper;
import com.labo.anapath.user.UserRepository;
import com.labo.anapath.user.UserResponseDto;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Implémentation du service d'authentification JWT avec support 2FA Google Authenticator.
 * <p>
 * Gère l'intégralité du flux d'authentification :
 * <ol>
 *   <li>Login par identifiants via Spring Security {@link AuthenticationManager}.</li>
 *   <li>Détection de la 2FA activée → émission d'un token temporaire de challenge (5 min).</li>
 *   <li>Validation du code TOTP → émission des tokens définitifs.</li>
 *   <li>Rafraîchissement des tokens via le refresh token.</li>
 *   <li>Logout avec blacklisting du token courant.</li>
 * </ol>
 * </p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final CustomUserDetailsService customUserDetailsService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final GoogleAuthenticator googleAuthenticator;
    private final PasswordEncoder passwordEncoder;
    private final TwoFaRepository twoFaRepository;
    private final com.labo.anapath.common.security.PolitiqueDeMotDePasse politiqueDeMotDePasse;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;
    private final TwoFaService twoFaService;
    private final com.labo.anapath.common.email.EmailService emailService;
    private final BranchRepository branchRepository;

    /**
     * Affiche le code OTP 2FA en clair dans les logs applicatifs.
     * <p>
     * {@code false} par défaut. À passer à {@code true} UNIQUEMENT sur un
     * environnement de test où l'envoi d'e-mails est indisponible (ex. quota
     * Mailtrap atteint), afin de lire le code dans {@code docker compose logs}.
     * NE JAMAIS activer en production : loguer un code d'authentification en
     * clair est une faille de sécurité.
     * </p>
     */
    @org.springframework.beans.factory.annotation.Value("${app.otp.log-plaintext:false}")
    private boolean logOtpPlaintext;

    /** Adresse du front, où mène le lien de réinitialisation envoyé par courriel. */
    @org.springframework.beans.factory.annotation.Value("${app.front-url}")
    private String frontUrl;

    /**
     * {@inheritDoc}
     * <p>
     * Délègue la vérification des identifiants à Spring Security. Si la 2FA est
     * activée, retourne un token temporaire. Sinon, met à jour {@code isConnect}
     * et {@code lastLoginDevice} en base avant de retourner les tokens définitifs.
     * </p>
     *
     * @throws UnauthorizedException si le compte est désactivé ou si les identifiants sont incorrects
     */
    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        if (userRepository.findByEmail(request.getEmail()).filter(this::estVerrouille).isPresent()) {
            log.warn("Échec de connexion (compte verrouillé) pour: {} depuis {}",
                    maskEmail(request.getEmail()), adresseClient());
            throw new UnauthorizedException("Identifiants invalides.");
        }
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

            User user = userRepository.findById(userPrincipal.getId())
                    .orElseThrow(() -> new UnauthorizedException("Utilisateur non trouvé"));

            // Le code à usage unique est exigé à CHAQUE connexion, pour tout le
            // monde. Il l'était auparavant selon `two_factor_enabled`, réglage
            // qu'un compte sur deux n'avait pas : la moitié des sessions
            // s'ouvraient sur le seul mot de passe.
            //
            // Le mot de passe est donc validé ici, mais aucun jeton d'accès
            // n'est délivré : seul un jeton temporaire de courte durée, que
            // `challenge()` échange contre les jetons définitifs une fois le
            // code vérifié. Il n'existe plus de chemin de connexion sans OTP.
            String tempToken = jwtTokenProvider.generateTempToken(userPrincipal.getId());

            // Qui a une application d'authentification lit son code sur son
            // téléphone : lui envoyer un courriel à chaque connexion serait un
            // message inutile de plus, et un second code valable en circulation.
            // Le courriel reste à un clic — `resend2FA` — si le téléphone
            // manque, de sorte que personne ne peut se retrouver enfermé dehors.
            final String canal;
            if (user.isTwoFactorEnabled() && user.getTwoFactorSecret() != null) {
                canal = "APP";
                log.info("Connexion par application attendue pour : {}", maskEmail(request.getEmail()));
            } else {
                canal = "EMAIL";
                sendAndStoreOtp(user);
                log.info("Code de connexion envoyé à : {}", maskEmail(request.getEmail()));
            }
            return LoginResponse.requires2fa(
                    tempToken, JwtTokenProvider.TEMP_TOKEN_VALIDITY_MS / 1000, canal);
        } catch (DisabledException ex) {
            log.warn("Échec de connexion (compte désactivé) pour: {}", maskEmail(request.getEmail()));
            throw new UnauthorizedException("Identifiants invalides.");
        } catch (BadCredentialsException ex) {
            log.warn("Échec de connexion (mauvais identifiants) pour: {}", maskEmail(request.getEmail()));
            throw new UnauthorizedException("Identifiants invalides.");
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Valide la signature, le type ({@code "refresh"}) et l'absence de blacklisting
     * avant d'émettre un nouveau couple access+refresh token. Vérifie également
     * que le compte est toujours actif.
     * </p>
     *
     * @throws UnauthorizedException si le refresh token est invalide, révoqué ou si le compte est désactivé
     */
    @Override
    @Transactional
    public LoginResponse refresh(String token) {
        if (!jwtTokenProvider.validateToken(token)) {
            throw new UnauthorizedException("Refresh token invalide ou expiré.");
        }
        String tokenType = jwtTokenProvider.extractType(token);
        if (!"refresh".equals(tokenType)) {
            throw new UnauthorizedException("Token fourni n'est pas un refresh token.");
        }
        String jti = jwtTokenProvider.extractJti(token);
        if (tokenBlacklistService.isBlacklisted(jti)) {
            throw new UnauthorizedException("Ce token a été révoqué.");
        }
        Instant oldRefreshExpiry = jwtTokenProvider.extractExpiry(token);
        tokenBlacklistService.blacklist(jti, oldRefreshExpiry);
        UUID userId = jwtTokenProvider.extractUserId(token);
        UserPrincipal userPrincipal = (UserPrincipal) customUserDetailsService.loadUserById(userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Utilisateur non trouvé"));
        String newAccessToken = jwtTokenProvider.generateToken(userPrincipal);
        // La fenêtre se recalcule à chaque rafraîchissement : un rôle retiré ou
        // accordé prend effet au renouvellement suivant, sans attendre une
        // reconnexion.
        String newRefreshToken =
                jwtTokenProvider.generateRefreshToken(userId, fenetreDInactivite(user));
        long expiresIn = jwtProperties.getExpirationMs() / 1000;
        if (!user.isActive()) {
            throw new UnauthorizedException("Compte désactivé.");
        }

        return new LoginResponse(newAccessToken, newRefreshToken, expiresIn, userMapper.toResponseDto(user));
    }

    /**
     * {@inheritDoc}
     * <p>
     * Le JTI du token est blacklisté jusqu'à son expiration naturelle. L'état
     * {@code isConnect} de l'utilisateur est mis à {@code false} en base. Si le
     * token est absent ou invalide, l'opération est ignorée sans erreur.
     * </p>
     */
    @Override
    public void logout(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        try {
            String jti = jwtTokenProvider.extractJti(token);
            if (StringUtils.hasText(jti)) {
                tokenBlacklistService.blacklist(jti, jwtTokenProvider.extractExpiry(token));
            }
            // Mise à jour du statut de connexion en base
            UUID userId = jwtTokenProvider.extractUserId(token);
            userRepository.findById(userId).ifPresent(user -> {
                user.setConnect(false);
                userRepository.save(user);
            });
            log.info("Déconnexion — token blacklisté (jti={})", jti);
        } catch (Exception ex) {
            log.warn("Logout — erreur lors du traitement du token: {}", ex.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Récupère l'entité utilisateur depuis la base en filtrant par UUID et succursale
     * pour garantir l'isolation multi-tenant, puis la convertit en DTO.
     * </p>
     *
     * @throws UnauthorizedException si l'utilisateur est introuvable dans la succursale du JWT
     */
    @Override
    @Transactional(readOnly = true)
    public UserResponseDto me(UUID userId, UUID branchId) {
        User user = userRepository.findByIdAndBranchId(userId, branchId)
                .orElseThrow(() -> new UnauthorizedException("Utilisateur non trouvé"));
        return userMapper.toResponseDto(user);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Délègue à la requête native {@link BranchRepository#findAccessibleBranches} (jointure
     * {@code branch_user}/{@code branches}) et mappe chaque ligne brute en
     * {@link UserBranchResponseDto}. Chaque ligne : {@code [id, name, code, location, is_default]}.
     * </p>
     */
    @Override
    @Transactional(readOnly = true)
    public List<UserBranchResponseDto> getUserBranches(UUID userId) {
        return branchRepository.findAccessibleBranches(userId).stream()
                .map(row -> new UserBranchResponseDto(
                        (UUID) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        Boolean.TRUE.equals(row[4])))
                .toList();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Le jeton ne quitte le serveur que par courriel : seule la boîte de
     * l'utilisateur prouve que la demande vient de lui. La base n'en garde que
     * l'empreinte SHA-256, si bien qu'une copie de la table ne permet pas de
     * réinitialiser un compte. Un e-mail inconnu ne produit rien, et la réponse
     * reste la même pour ne pas révéler quels comptes existent.
     * </p>
     */
    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            user.setResetToken(sha256(token));
            user.setResetTokenExpiresAt(LocalDateTime.now().plusHours(1));
            userRepository.save(user);
            String lien = frontUrl + "/reset-password?token=" + token
                    + "&email=" + URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8);
            emailService.sendPasswordReset(user.getEmail(), user.getFirstname(), lien);
            log.info("Lien de réinitialisation envoyé à: {}", maskEmail(request.getEmail()));
        });
    }

    private static String sha256(String valeur) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(valeur.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Recherche l'utilisateur par son token de réinitialisation et vérifie que ce token
     * n'est pas expiré. Valide la correspondance des deux mots de passe, encode le nouveau
     * mot de passe en BCrypt, le persiste et efface les champs de token.
     * </p>
     *
     * @throws UnauthorizedException si le token est invalide ou expiré
     * @throws BusinessException     si les mots de passe ne correspondent pas
     */
    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getPassword().equals(request.getPasswordConfirmation())) {
            throw new BusinessException("Les mots de passe ne correspondent pas");
        }
        User user = userRepository.findByResetToken(sha256(request.getToken()))
                .filter(u -> u.getResetTokenExpiresAt() != null
                        && u.getResetTokenExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new UnauthorizedException("Token de réinitialisation invalide ou expiré"));

        politiqueDeMotDePasse.verifier(request.getPassword(), user.getEmail(), user.getFirstname(), user.getLastname());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);
        userRepository.save(user);
        log.info("Mot de passe réinitialisé pour userId={}", user.getId());
    }

    /**
     * {@inheritDoc}
     * <p>
     * Génère un code OTP à 6 chiffres, le stocke haché dans la table {@code two_fas}
     * et l'envoie par email. Si l'email est introuvable, opération silencieuse.
     * </p>
     */
    @Override
    @Transactional
    public void resend2FA(Resend2FARequest request) {
        java.util.Optional<User> cible;
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            cible = userRepository.findByEmail(request.getEmail());
        } else if (StringUtils.hasText(request.getTempToken())
                && jwtTokenProvider.validateToken(request.getTempToken())
                && "2fa-challenge".equals(jwtTokenProvider.extractType(request.getTempToken()))) {
            cible = userRepository.findById(jwtTokenProvider.extractUserId(request.getTempToken()));
        } else {
            cible = java.util.Optional.empty();
        }
        // Silence dans tous les cas : dire « adresse inconnue » révélerait les comptes.
        cible.ifPresent(user -> {
            sendAndStoreOtp(user);
            log.info("OTP renvoyé par email à: {}", maskEmail(user.getEmail()));
        });
    }

    private void sendAndStoreOtp(User user) {
        SecureRandom secureRandom = new SecureRandom();
        int otpInt = 100_000 + secureRandom.nextInt(900_000);
        String otp = String.valueOf(otpInt);
        String hashedOtp = passwordEncoder.encode(otp);

        // Supprimer l'ancien code (bulk JPQL) puis insérer le nouveau
        twoFaRepository.deleteByUserId(user.getId());
        twoFaRepository.flush();
        twoFaRepository.save(new TwoFa(user.getId(), user.getBranchId(), hashedOtp));

        // DEV UNIQUEMENT : trace le code en clair quand l'envoi d'e-mail est
        // indisponible (voir app.otp.log-plaintext). Jamais en production.
        if (logOtpPlaintext) {
            log.warn("[DEV] Code OTP 2FA pour {} : {}", maskEmail(user.getEmail()), otp);
        }

        // Envoyer l'email (async)
        emailService.sendOtp(user.getEmail(), user.getFirstname(), otp);
    }

    /** Codes faux consécutifs avant d'invalider le code en cours. */
    static final int ECHECS_AVANT_NOUVEAU_CODE = 5;
    /** Codes faux dans la fenêtre d'une heure avant de verrouiller le compte. */
    static final int ECHECS_AVANT_VERROU = 10;
    static final int MINUTES_DE_VERROU = 15;

    private boolean estVerrouille(User user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now());
    }

    /**
     * Compte un code faux pour ce compte et rend l'exception à lever.
     *
     * <p>Au 5ᵉ échec, le code en cours est invalidé : il faut en redemander un,
     * ce qui coupe toute énumération. Au 10ᵉ échec en une heure, le compte est
     * verrouillé un quart d'heure. Le compteur est sauvé ici même, car l'exception
     * levée ensuite annule la transaction.</p>
     */
    private InvalidCodeException codeFaux(User user, String message) {
        LocalDateTime maintenant = LocalDateTime.now();
        if (user.getOtpFailuresSince() == null || user.getOtpFailuresSince().plusHours(1).isBefore(maintenant)) {
            user.setOtpFailuresSince(maintenant);
            user.setOtpFailedAttempts((short) 0);
        }
        user.setOtpFailedAttempts((short) (user.getOtpFailedAttempts() + 1));
        int echecs = user.getOtpFailedAttempts();
        boolean invaliderLeCode = echecs % ECHECS_AVANT_NOUVEAU_CODE == 0;
        if (echecs >= ECHECS_AVANT_VERROU) {
            user.setLockedUntil(maintenant.plusMinutes(MINUTES_DE_VERROU));
            invaliderLeCode = true;
            log.warn("Compte verrouillé {} min après {} codes faux : {} depuis {}",
                    MINUTES_DE_VERROU, echecs, maskEmail(user.getEmail()), adresseClient());
            message = "Code invalide.";
        } else if (invaliderLeCode) {
            log.warn("Code invalidé après {} codes faux : {} depuis {}",
                    echecs, maskEmail(user.getEmail()), adresseClient());
            message = "Code invalide. Veuillez en demander un nouveau.";
        }
        sauverHorsTransaction(user, invaliderLeCode);
        return new InvalidCodeException(message);
    }

    /**
     * Le compteur, et l'invalidation du code, doivent survivre à l'exception
     * qui va annuler la transaction appelante : transaction à part.
     */
    private void sauverHorsTransaction(User user, boolean invaliderLeCode) {
        var t = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        t.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        t.executeWithoutResult(status -> {
            userRepository.saveAndFlush(user);
            if (invaliderLeCode) {
                twoFaRepository.deleteByUserId(user.getId());
            }
        });
    }

    /** Adresse du client, telle que Tomcat l'a rétablie derrière nginx (voir server.forward-headers-strategy). */
    private String adresseClient() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attrs == null ? "?" : attrs.getRequest().getRemoteAddr();
        } catch (Exception e) {
            return "?";
        }
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        String[] parts = email.split("@", 2);
        String user = parts[0];
        String masked = user.length() > 2
                ? user.charAt(0) + "***" + user.charAt(user.length() - 1)
                : "***";
        return masked + "@" + parts[1];
    }

    /**
     * Calcule le hash SHA-256 de l'en-tête {@code User-Agent} de la requête courante.
     * <p>
     * Utilisé pour stocker une empreinte du dispositif de connexion sans conserver
     * la valeur brute de l'User-Agent en base.
     * </p>
     *
     * @return hash hexadécimal SHA-256 du User-Agent, ou {@code null} si non disponible
     */
    /**
     * Le silence que la session d'une personne peut supporter.
     *
     * <p>Une heure pour un médecin, trente minutes pour tout le reste. Ce n'est
     * pas une faveur : un pathologiste quitte son écran pour lire une lame et y
     * revient, et le déconnecter au milieu lui fait rouvrir une session pour
     * finir une phrase. Un poste du comptoir ou du laboratoire, lui, reste à
     * portée de qui passe dans le couloir — la fenêtre y est deux fois plus
     * courte, et c'est le sens de cette différence.</p>
     *
     * <p>Le rôle et non la permission : aucune permission ne distingue ces
     * métiers, et en créer une pour cette seule durée ferait une notion de plus
     * à tenir dans les rôles. Le slug est celui qu'emploient déjà les autres
     * règles de métier du serveur.</p>
     *
     * <p>Recalculée à chaque rafraîchissement : une session ouverte avant un
     * changement de rôle suit la nouvelle règle dès le renouvellement suivant,
     * sans attendre une reconnexion.</p>
     */
    private java.time.Duration fenetreDInactivite(User utilisateur) {
        boolean medecin = utilisateur.getRoles() != null
                && utilisateur.getRoles().stream()
                        .anyMatch(r -> "docteur".equalsIgnoreCase(r.getSlug()));
        return java.time.Duration.ofMillis(medecin
                ? jwtProperties.getRefreshExpirationMedecinMs()
                : jwtProperties.getRefreshExpirationMs());
    }

    private String getUserAgentHash() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            String userAgent = attrs.getRequest().getHeader("User-Agent");
            if (!StringUtils.hasText(userAgent)) return null;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(userAgent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            log.warn("Impossible de calculer le hash User-Agent: {}", e.getMessage());
            return null;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Vérifie la validité et le type du token temporaire, puis valide le code OTP email
     * depuis la table {@code two_fas}. Vérifie l'expiration (10 min) et la correspondance
     * bcrypt. En cas de succès, émet les tokens définitifs et met à jour l'état de connexion.
     * </p>
     *
     * @throws UnauthorizedException si le token temporaire est invalide ou si l'utilisateur est introuvable
     * @throws InvalidCodeException  si le code OTP est incorrect ou expiré
     */
    @Override
    @Transactional
    public LoginResponse challenge(TwoFactorVerifyRequest request) {
        String tempToken = request.getTempToken();
        if (!StringUtils.hasText(tempToken) || !jwtTokenProvider.validateToken(tempToken)) {
            throw new UnauthorizedException("Token temporaire invalide ou expiré.");
        }
        String tokenType = jwtTokenProvider.extractType(tempToken);
        if (!"2fa-challenge".equals(tokenType)) {
            throw new UnauthorizedException("Token temporaire invalide.");
        }

        UUID userId = jwtTokenProvider.extractUserId(tempToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Utilisateur introuvable."));

        // Les deux canaux sont acceptés, sans que l'utilisateur ait à dire
        // lequel il emploie : six chiffres sont six chiffres. C'est ce qui rend
        // l'enfermement impossible — téléphone perdu, on demande un code par
        // courriel ; boîte inaccessible, on lit l'application.
        //
        // L'application est essayée en premier : c'est la voie normale d'un
        // utilisateur équipé, et le plus souvent aucun code n'a été envoyé.
        // Un compte verrouillé répond comme à un code faux : rien n'indique
        // de l'extérieur que le verrou existe.
        if (estVerrouille(user)) {
            throw new InvalidCodeException("Code invalide.");
        }

        boolean parApplication = twoFaService.verifierCodeApplication(userId, request.getCode());

        if (!parApplication) {
            TwoFa twoFa = twoFaRepository.findByUserId(userId)
                    .orElseThrow(() -> codeFaux(user, "Code invalide ou expiré."));

            // Vérifier l'expiration (10 minutes)
            if (twoFa.getCreatedAt().plusMinutes(10).isBefore(LocalDateTime.now())) {
                twoFaRepository.deleteByUserId(userId);
                throw new InvalidCodeException("Code expiré. Veuillez en demander un nouveau.");
            }

            // Vérifier le code (comparaison bcrypt)
            if (!passwordEncoder.matches(request.getCode().trim(), twoFa.getCode())) {
                throw codeFaux(user, "Code invalide.");
            }

            // Supprimer le code utilisé
            twoFaRepository.deleteByUserId(userId);
        }
        user.setOtpFailedAttempts((short) 0);
        user.setOtpFailuresSince(null);

        // Blacklister le tempToken
        String tempJti = jwtTokenProvider.extractJti(tempToken);
        if (tempJti != null) {
            tokenBlacklistService.blacklist(tempJti, jwtTokenProvider.extractExpiry(tempToken));
        }

        UserPrincipal userPrincipal = (UserPrincipal) customUserDetailsService.loadUserById(userId);
        String accessToken = jwtTokenProvider.generateToken(userPrincipal);
        String refreshToken =
                jwtTokenProvider.generateRefreshToken(userId, fenetreDInactivite(user));
        long expiresIn = jwtProperties.getExpirationMs() / 1000;

        user.setConnect(true);
        user.setLastLoginDevice(getUserAgentHash());
        userRepository.save(user);

        log.info("2FA challenge réussi pour userId={}", userId);
        return new LoginResponse(accessToken, refreshToken, expiresIn, userMapper.toResponseDto(user));
    }
}

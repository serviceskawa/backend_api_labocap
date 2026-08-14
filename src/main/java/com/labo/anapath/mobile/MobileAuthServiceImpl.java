package com.labo.anapath.mobile;

import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.exception.UnauthorizedException;
import com.labo.anapath.common.security.CustomUserDetailsService;
import com.labo.anapath.common.security.JwtProperties;
import com.labo.anapath.common.security.JwtTokenProvider;
import com.labo.anapath.common.security.UserPrincipal;
import com.labo.anapath.mobile.MobileDtos.*;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Authentification de l'application mobile de signature.
 *
 * <h2>Pourquoi une chaîne distincte du web</h2>
 *
 * <p>Le web ouvre une session derrière un mot de passe et un code envoyé par
 * courriel. Sur un téléphone posé au comptoir, ni l'un ni l'autre ne conviennent :
 * on ne saisit pas un mot de passe long entre deux patients, et la boîte mail
 * n'est pas à portée. L'appareil enrôlé remplace le mot de passe comme preuve
 * durable, le PIN ne fait que le déverrouiller.</p>
 *
 * <h2>Ce que la session obtenue vaut</h2>
 *
 * <p>Le jeton produit est le jeton ordinaire de la plateforme, porteur des mêmes
 * permissions. Toute la chaîne {@code @PreAuthorize} s'applique donc sans
 * modification, et le journal impute les actes au même utilisateur que sur le
 * web — c'est précisément ce qu'on veut pour une signature.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MobileAuthServiceImpl implements MobileAuthService {

    /** Au-delà, le PIN est gelé : un code court se force autrement en quelques minutes. */
    private static final int TENTATIVES_MAX = 5;

    /** Durée du gel. Assez long pour ruiner une attaque, assez court pour ne pas punir une méprise. */
    private static final int MINUTES_VERROU = 15;

    /** Un code d'enrôlement se transmet et s'emploie dans la foulée. */
    private static final int HEURES_VALIDITE_CODE = 24;

    /** Droit d'employer l'application, attribué utilisateur par utilisateur. */
    private static final String PERMISSION_MOBILE = "use-mobile-app";

    private static final SecureRandom ALEA = new SecureRandom();

    private final MobileDeviceRepository deviceRepository;
    private final MobileEnrollmentCodeRepository codeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final CustomUserDetailsService userDetailsService;
    private final com.labo.anapath.auth.AuthService authService;

    @Override
    @Transactional
    public EnrollmentCodeResponse creerCodeEnrolement(UUID userId, UUID auteurId, UUID branchId) {
        User user = userRepository.findByIdAndBranchId(userId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));

        // On refuse d'emblée d'enrôler quelqu'un qui n'a pas le droit d'employer
        // l'application : sans cela, on distribuerait des codes qui butent à la
        // première connexion, sans que personne comprenne pourquoi.
        if (!aLaPermissionMobile(user.getId())) {
            throw new UnauthorizedException(
                    "Cet utilisateur n'a pas le droit « " + PERMISSION_MOBILE + " ». "
                            + "Accordez-le avant d'enrôler un appareil.");
        }

        String code = genererCode();
        LocalDateTime expiration = LocalDateTime.now().plusHours(HEURES_VALIDITE_CODE);
        codeRepository.save(new MobileEnrollmentCode(
                user.getId(), passwordEncoder.encode(code), expiration, auteurId));

        log.info("Code d'enrôlement mobile créé pour userId={} par userId={}", user.getId(), auteurId);
        // Seul instant où le code existe en clair — la base n'en garde que l'empreinte.
        return new EnrollmentCodeResponse(code, expiration);
    }

    @Override
    @Transactional
    public EnrollResponse enroler(EnrollRequest requete) {
        User user = userRepository.findByEmail(requete.email().trim().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException("Code d'enrôlement invalide."));

        MobileEnrollmentCode code = codeRepository
                .findByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
                .stream()
                .filter(MobileEnrollmentCode::estUtilisable)
                .filter(c -> passwordEncoder.matches(requete.code().trim(), c.getCodeHash()))
                .findFirst()
                // Message volontairement identique à celui de l'utilisateur inconnu :
                // distinguer les deux cas dirait à un inconnu quelles adresses existent.
                .orElseThrow(() -> new UnauthorizedException("Code d'enrôlement invalide."));

        if (!aLaPermissionMobile(user.getId())) {
            throw new UnauthorizedException("Cet utilisateur n'a pas accès à l'application mobile.");
        }

        MobileDevice appareil = deviceRepository.save(new MobileDevice(
                user.getId(), user.getBranchId(), requete.label().trim(), requete.publicKey()));

        code.setUsedAt(LocalDateTime.now());
        code.setDeviceId(appareil.getId());
        codeRepository.save(code);

        log.info("Appareil mobile enrôlé : deviceId={} userId={}", appareil.getId(), user.getId());
        return new EnrollResponse(appareil.getId(), appareil.getLabel(),
                appareil.getEnrolledAt(), user.getPinHash() != null);
    }

    @Override
    @Transactional
    public MobileLoginResponse connecter(MobileLoginRequest requete) {
        MobileDevice appareil = deviceRepository.findByIdAndRevokedAtIsNull(requete.deviceId())
                .orElseThrow(() -> new UnauthorizedException("Appareil inconnu ou révoqué."));

        User user = userRepository.findById(appareil.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Appareil inconnu ou révoqué."));

        if (!user.isActive()) {
            throw new UnauthorizedException("Ce compte est désactivé.");
        }
        if (user.getPinHash() == null) {
            throw new UnauthorizedException("Aucun code PIN n'est défini pour ce compte.");
        }
        if (user.getPinLockedUntil() != null && user.getPinLockedUntil().isAfter(LocalDateTime.now())) {
            throw new UnauthorizedException("Trop de tentatives. Réessayez dans quelques minutes.");
        }
        if (!aLaPermissionMobile(user.getId())) {
            throw new UnauthorizedException("Cet utilisateur n'a pas accès à l'application mobile.");
        }

        if (!passwordEncoder.matches(requete.pin(), user.getPinHash())) {
            enregistrerEchec(user);
            throw new UnauthorizedException("Code PIN incorrect.");
        }

        user.setPinFailedAttempts((short) 0);
        user.setPinLockedUntil(null);
        userRepository.save(user);

        appareil.setLastSeenAt(LocalDateTime.now());
        deviceRepository.save(appareil);

        // Jeton ordinaire de la plateforme : mêmes permissions, même journal.
        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserById(user.getId());
        List<String> permissions = principal.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .toList();

        log.info("Connexion mobile réussie : deviceId={} userId={}", appareil.getId(), user.getId());
        return new MobileLoginResponse(
                jwtTokenProvider.generateToken(principal, appareil.getId()),
                jwtTokenProvider.generateRefreshToken(user.getId()),
                jwtProperties.getExpirationMs() / 1000,
                user.getId(),
                user.getFirstname() + " " + user.getLastname(),
                user.getBranchId(),
                permissions);
    }

    /**
     * Renouvelle la session sans redemander le PIN.
     *
     * <p>Délègue à {@code AuthService.refresh}, qui valide le jeton, vérifie son
     * type, refuse ceux déjà révoqués et met le jeton consommé en liste noire.
     * Réécrire cette rotation ici aurait signifié la maintenir en double, et
     * qu'un correctif de sécurité appliqué d'un côté manque de l'autre.</p>
     *
     * <p>Seul l'emballage change : le web renvoie ses jetons par cookies
     * HttpOnly, l'application les reçoit dans le corps.</p>
     */
    @Override
    @Transactional
    public MobileLoginResponse rafraichir(MobileRefreshRequest requete) {
        var reponse = authService.refresh(requete.refreshToken());

        UUID userId = jwtTokenProvider.extractUserId(reponse.accessToken());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Session invalide."));

        // L'appareil est revalidé à chaque renouvellement, et le jeton reconduit
        // reporte sa provenance. Sans cela, il perdrait la revendication posée à
        // la connexion, et avec elle l'obligation de signer les validations : il
        // aurait suffi d'attendre un renouvellement pour s'en affranchir.
        MobileDevice appareil = deviceRepository.findByIdAndRevokedAtIsNull(requete.deviceId())
                .orElseThrow(() -> new UnauthorizedException("Appareil inconnu ou révoqué."));
        if (!appareil.getUserId().equals(userId)) {
            throw new UnauthorizedException("Cet appareil n'appartient pas au titulaire de la session.");
        }
        appareil.setLastSeenAt(LocalDateTime.now());
        deviceRepository.save(appareil);

        // Le droit d'employer l'application est revérifié à chaque renouvellement :
        // le retirer à quelqu'un doit le mettre dehors au plus tard à l'expiration
        // du jeton courant, sans attendre qu'il se déconnecte de lui-même.
        if (!aLaPermissionMobile(userId)) {
            throw new UnauthorizedException("Cet utilisateur n'a plus accès à l'application mobile.");
        }

        UserPrincipal principal = (UserPrincipal) userDetailsService.loadUserById(userId);
        return new MobileLoginResponse(
                // Réémis avec la revendication d'appareil : celui d'AuthService
                // ne la porte pas, puisqu'il sert aussi le web.
                jwtTokenProvider.generateToken(principal, appareil.getId()),
                reponse.refreshToken(),
                jwtProperties.getExpirationMs() / 1000,
                userId,
                user.getFirstname() + " " + user.getLastname(),
                user.getBranchId(),
                principal.getAuthorities().stream().map(a -> a.getAuthority()).toList());
    }

    @Override
    @Transactional
    public void definirPin(UUID userId, String pin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));
        user.setPinHash(passwordEncoder.encode(pin));
        user.setPinFailedAttempts((short) 0);
        user.setPinLockedUntil(null);
        userRepository.save(user);
        log.info("Code PIN mobile défini pour userId={}", userId);
    }

    @Override
    @Transactional
    public void revoquer(UUID deviceId, UUID auteurId, UUID branchId) {
        MobileDevice appareil = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Appareil", deviceId));
        if (branchId != null && !branchId.equals(appareil.getBranchId())) {
            throw new ResourceNotFoundException("Appareil", deviceId);
        }
        if (!appareil.estActif()) {
            return; // Déjà révoqué : rien à faire, et le redire n'apporte rien.
        }
        appareil.setRevokedAt(LocalDateTime.now());
        appareil.setRevokedBy(auteurId);
        deviceRepository.save(appareil);
        log.warn("Appareil mobile révoqué : deviceId={} par userId={}", deviceId, auteurId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeviceResponse> listerAppareils(UUID branchId) {
        return DeviceResponse.de(deviceRepository.findByBranchIdOrderByEnrolledAtDesc(branchId));
    }

    // -----------------------------------------------------------------------

    private void enregistrerEchec(User user) {
        short tentatives = (short) (user.getPinFailedAttempts() + 1);
        user.setPinFailedAttempts(tentatives);
        if (tentatives >= TENTATIVES_MAX) {
            user.setPinLockedUntil(LocalDateTime.now().plusMinutes(MINUTES_VERROU));
            log.warn("PIN mobile gelé {} min après {} échecs — userId={}",
                    MINUTES_VERROU, tentatives, user.getId());
        }
        userRepository.save(user);
    }

    /**
     * L'utilisateur a-t-il le droit d'employer l'application ?
     *
     * <p>La vérification regarde les rôles <strong>et</strong> les permissions
     * directes, alors que {@code CustomUserDetailsService.extractPermissions} ne
     * lit que les rôles. Ce n'est pas un oubli de ma part mais un contournement
     * assumé : la table {@code users_permissions} est alimentée par l'écran
     * d'administration et affichée dans la fiche, mais ses lignes ne deviennent
     * jamais des autorités Spring. Elles sont donc sans effet aujourd'hui.</p>
     *
     * <p>Or le client exige que l'accès à l'application se donne utilisateur par
     * utilisateur — c'est exactement à quoi sert cette table. On la lit ici
     * directement plutôt que de corriger l'extraction globale, qui accorderait
     * d'un coup à quelques comptes les centaines de permissions directes déjà
     * enregistrées. Ce redressement-là mérite sa propre décision.</p>
     */
    private boolean aLaPermissionMobile(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        boolean parRole = user.getRoles().stream()
                .flatMap(r -> r.getPermissions().stream())
                .anyMatch(p -> PERMISSION_MOBILE.equals(p.getSlug()));
        boolean enDirect = user.getDirectPermissions().stream()
                .anyMatch(p -> PERMISSION_MOBILE.equals(p.getSlug()));
        return parRole || enDirect;
    }

    /**
     * Code d'enrôlement à huit caractères, tiré d'un alphabet sans O/0 ni I/1 :
     * il est lu à voix haute ou recopié depuis un papier, et ces confusions-là
     * coûtent un aller-retour à chaque fois.
     */
    private String genererCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(alphabet.charAt(ALEA.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}

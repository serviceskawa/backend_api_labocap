package com.labo.anapath.auth;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.labo.anapath.common.exception.InvalidCodeException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * Implémentation du service 2FA basé sur le protocole TOTP (RFC 6238) via Google Authenticator.
 * <p>
 * Gère le cycle de vie complet de la 2FA pour un utilisateur :
 * génération du secret, génération du QR code (URI {@code otpauth://totp/...} encodée en image PNG base64),
 * activation après vérification et désactivation.
 * </p>
 * <p>
 * Le nom de l'application ({@code issuer}) affiché dans Google Authenticator est lu depuis
 * {@code spring.application.name} (valeur par défaut : "Labo AnaPath").
 * </p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TwoFaServiceImpl implements TwoFaService {

    private final UserRepository userRepository;
    private final GoogleAuthenticator googleAuthenticator;

    /** Nom de l'application affiché comme émetteur dans Google Authenticator. */
    @Value("${spring.application.name:Labo AnaPath}")
    private String appName;

    /**
     * {@inheritDoc}
     * <p>
     * Génère un nouveau secret TOTP via {@link GoogleAuthenticator#createCredentials()},
     * le persiste en base et retourne le QR code PNG encodé en base64.
     * </p>
     */
    @Override
    @Transactional
    public TwoFaSetupResponse setup(UUID userId) {
        User user = loadUser(userId);

        GoogleAuthenticatorKey credentials = googleAuthenticator.createCredentials();
        String secret = credentials.getKey();

        user.setTwoFactorSecret(secret);
        userRepository.save(user);

        String qrCodeBase64 = generateQrCode(user.getEmail(), secret);
        return new TwoFaSetupResponse(secret, qrCodeBase64);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Vérifie que le secret a été préalablement généré (via {@code /setup}), puis valide
     * le code TOTP. En cas de succès, positionne {@code twoFactorEnabled = true} en base.
     * </p>
     *
     * @throws com.labo.anapath.common.exception.InvalidCodeException si le code est invalide ou la 2FA non initialisée
     */
    @Override
    @Transactional
    public void verifyAndEnable(UUID userId, String code) {
        User user = loadUser(userId);
        if (user.getTwoFactorSecret() == null) {
            throw new InvalidCodeException("2FA non initialisée. Appelez /setup d'abord.");
        }
        if (!googleAuthenticator.authorize(user.getTwoFactorSecret(), parseCode(code))) {
            throw new InvalidCodeException("Code TOTP invalide");
        }
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
    }

    /** Durée d'un pas TOTP, en secondes. Celle de la RFC 6238, et de tous les clients. */
    private static final long PAS_SECONDES = 30;

    /**
     * Tolérance d'horloge, en pas, de part et d'autre de l'instant courant.
     *
     * <p>Un téléphone dérive de quelques secondes ; refuser un code d'un pas
     * voisin ferait échouer des saisies correctes sans rien protéger de plus.
     * C'est aussi la tolérance par défaut de la bibliothèque.</p>
     */
    private static final int TOLERANCE_PAS = 1;

    /** {@inheritDoc} */
    @Override
    @Transactional
    public boolean verifierCodeApplication(UUID userId, String code) {
        User user = loadUser(userId);
        if (!user.isTwoFactorEnabled() || user.getTwoFactorSecret() == null) {
            return false;
        }

        final int saisi;
        try {
            saisi = parseCode(code);
        } catch (Exception e) {
            return false;
        }

        long pasCourant = System.currentTimeMillis() / 1000 / PAS_SECONDES;

        // On parcourt la fenêtre du plus récent au plus ancien : un code saisi
        // à l'instant appartient presque toujours au pas courant, et l'ordre
        // évite d'attribuer par erreur un pas ancien à un code ambigu.
        for (int decalage = TOLERANCE_PAS; decalage >= -TOLERANCE_PAS; decalage--) {
            long pas = pasCourant + decalage;
            if (!googleAuthenticator.authorize(
                    user.getTwoFactorSecret(), saisi, pas * PAS_SECONDES * 1000)) {
                continue;
            }

            // Le pas doit être strictement postérieur au dernier accepté. Un
            // code déjà servi, ou antérieur, est refusé même s'il est
            // arithmétiquement juste.
            Long dernier = user.getTwoFactorLastStep();
            if (dernier != null && pas <= dernier) {
                log.warn("Rejeu d'un code d'application refusé pour l'utilisateur {}", userId);
                return false;
            }

            user.setTwoFactorLastStep(pas);
            userRepository.save(user);
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Vérifie que la 2FA est bien activée, valide le code TOTP puis supprime le secret
     * et désactive la 2FA ({@code twoFactorEnabled = false}, {@code twoFactorSecret = null}).
     * </p>
     *
     * @throws com.labo.anapath.common.exception.InvalidCodeException si le code est invalide ou la 2FA non activée
     */
    @Override
    @Transactional
    public void disable(UUID userId, String code) {
        User user = loadUser(userId);
        if (user.getTwoFactorSecret() == null || !user.isTwoFactorEnabled()) {
            throw new InvalidCodeException("La 2FA n'est pas activée sur ce compte");
        }
        if (!googleAuthenticator.authorize(user.getTwoFactorSecret(), parseCode(code))) {
            throw new InvalidCodeException("Code TOTP invalide");
        }
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userRepository.save(user);
    }

    /**
     * Charge un utilisateur depuis la base ou lève une exception si introuvable.
     *
     * @param userId UUID de l'utilisateur à charger
     * @return entité utilisateur
     * @throws com.labo.anapath.common.exception.ResourceNotFoundException si l'utilisateur n'existe pas
     */
    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    /**
     * Convertit un code TOTP sous forme de chaîne en entier.
     *
     * @param code code TOTP à convertir
     * @return valeur entière du code
     * @throws com.labo.anapath.common.exception.InvalidCodeException si le code n'est pas numérique
     */
    private int parseCode(String code) {
        try {
            return Integer.parseInt(code.trim());
        } catch (NumberFormatException e) {
            throw new InvalidCodeException("Le code TOTP doit être numérique");
        }
    }

    /**
     * Génère un QR code PNG encodé en base64 à partir de l'URI TOTP standard.
     * <p>
     * L'URI générée suit le format {@code otpauth://totp/<issuer>:<account>?secret=...&issuer=...}
     * compatible avec Google Authenticator et la plupart des applications TOTP.
     * </p>
     *
     * @param email  adresse email de l'utilisateur (identifiant de compte dans le QR code)
     * @param secret secret TOTP en base32
     * @return image PNG du QR code encodée en base64
     * @throws RuntimeException si la génération du QR code échoue
     */
    private String generateQrCode(String email, String secret) {
        try {
            String issuer = URLEncoder.encode(appName, StandardCharsets.UTF_8);
            String account = URLEncoder.encode(email, StandardCharsets.UTF_8);
            String totpUri = String.format(
                    "otpauth://totp/%s:%s?secret=%s&issuer=%s",
                    issuer, account, secret, issuer);

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(totpUri, BarcodeFormat.QR_CODE, 200, 200);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            return Base64.getEncoder().encodeToString(pngOutputStream.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la génération du QR code", e);
        }
    }
}

package com.labo.anapath.auth;

import java.util.UUID;

/**
 * Contrat du service de gestion de l'authentification à deux facteurs (2FA).
 * <p>
 * Implémente le protocole TOTP (Time-based One-Time Password) via Google Authenticator.
 * Le cycle de vie de la 2FA sur un compte suit les étapes :
 * <ol>
 *   <li>{@link #setup} — génère un secret TOTP et retourne le QR code à scanner.</li>
 *   <li>{@link #verifyAndEnable} — valide le premier code pour confirmer la configuration
 *       et active officiellement la 2FA.</li>
 *   <li>{@link #disable} — désactive la 2FA après vérification du code courant.</li>
 * </ol>
 * </p>
 */
public interface TwoFaService {

    /**
     * Initialise la configuration 2FA pour un utilisateur.
     * <p>
     * Génère un nouveau secret TOTP, le persiste en base et retourne le QR code
     * (image PNG encodée en base64) à scanner avec Google Authenticator.
     * La 2FA n'est pas encore activée : il faut appeler {@link #verifyAndEnable} ensuite.
     * </p>
     *
     * @param userId UUID de l'utilisateur pour lequel configurer la 2FA
     * @return réponse contenant le secret TOTP et le QR code base64
     */
    TwoFaSetupResponse setup(UUID userId);

    /**
     * Vérifie le premier code TOTP et active la 2FA sur le compte.
     * <p>
     * Doit être appelé après {@link #setup} pour confirmer que l'utilisateur
     * a correctement scanné le QR code.
     * </p>
     *
     * @param userId UUID de l'utilisateur
     * @param code   code TOTP à 6 chiffres généré par l'application
     * @throws com.labo.anapath.common.exception.InvalidCodeException si le code est invalide ou si la 2FA n'a pas été initialisée
     */
    void verifyAndEnable(UUID userId, String code);

    /**
     * Désactive la 2FA sur le compte après vérification du code TOTP courant.
     * <p>
     * Le secret TOTP est supprimé de la base après désactivation réussie.
     * </p>
     *
     * @param userId UUID de l'utilisateur
     * @param code   code TOTP à 6 chiffres pour confirmer la désactivation
     * @throws com.labo.anapath.common.exception.InvalidCodeException si le code est invalide ou si la 2FA n'est pas activée
     */
    void disable(UUID userId, String code);

    /**
     * Vérifie un code d'application d'authentification à la connexion, et le
     * consomme.
     *
     * <p>Distincte de {@link #verifyAndEnable} : celle-ci sert à la mise en
     * place, une fois, et n'a pas à se prémunir du rejeu — le compte n'est pas
     * encore protégé. Celle-ci sert à chaque connexion, et un code reste
     * valable toute sa fenêtre de trente secondes. Sans consommation, six
     * chiffres lus par-dessus une épaule ouvrent une seconde session avant
     * d'expirer.</p>
     *
     * @return {@code false} si l'utilisateur n'a pas d'application, si le code
     *         ne correspond à aucun pas de la fenêtre, ou s'il a déjà servi
     */
    boolean verifierCodeApplication(java.util.UUID userId, String code);
}

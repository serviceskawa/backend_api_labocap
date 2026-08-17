package com.labo.anapath.mobile;

import com.labo.anapath.mobile.MobileDtos.*;

import java.util.List;
import java.util.UUID;

public interface MobileAuthService {

    /**
     * Ouvre l'accès mobile à un utilisateur, en un seul geste.
     *
     * <p>Accorde le droit, engendre un code PIN et un code d'enrôlement, et les
     * renvoie en clair — la seule et unique fois. Réunir les trois évite qu'un
     * agent reparte avec un accès à moitié posé, et supprime le tour de passe-
     * passe qu'exigeait la pose du PIN par l'appareil lui-même.</p>
     */
    AccesMobileResponse ouvrirAcces(UUID userId, UUID auteurId, UUID branchId);

    /**
     * Ferme l'accès : retire le droit, efface le PIN, révoque les appareils.
     *
     * <p>Les trois ensemble, car n'en faire qu'un laisserait une porte : un
     * appareil enrôlé continuerait d'ouvrir des sessions avec un PIN encore
     * valide, quand bien même le droit aurait disparu.</p>
     */
    void fermerAcces(UUID userId, UUID auteurId, UUID branchId);

    /** État de l'accès d'un utilisateur, pour l'écran d'administration. */
    EtatAccesResponse etatAcces(UUID userId, UUID branchId);

    /** Délivre un code d'enrôlement à usage unique pour un utilisateur. */
    EnrollmentCodeResponse creerCodeEnrolement(UUID userId, UUID auteurId, UUID branchId);

    /** Échange un code d'enrôlement contre l'identité d'un appareil. */
    EnrollResponse enroler(EnrollRequest requete);

    /** Ouvre une session depuis un appareil enrôlé, déverrouillé par son PIN. */
    MobileLoginResponse connecter(MobileLoginRequest requete);

    /** Renouvelle une session mobile à partir de son jeton de rafraîchissement. */
    MobileLoginResponse rafraichir(MobileRefreshRequest requete);

    /** Pose ou remplace le code PIN de l'utilisateur courant. */
    void definirPin(UUID userId, String pin);

    /** Coupe un appareil sans effacer la trace des actes qu'il a portés. */
    void revoquer(UUID deviceId, UUID auteurId, UUID branchId);

    List<DeviceResponse> listerAppareils(UUID branchId);
}

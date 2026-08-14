package com.labo.anapath.mobile;

import com.labo.anapath.mobile.MobileDtos.*;

import java.util.List;
import java.util.UUID;

public interface MobileAuthService {

    /** Délivre un code d'enrôlement à usage unique pour un utilisateur. */
    EnrollmentCodeResponse creerCodeEnrolement(UUID userId, UUID auteurId, UUID branchId);

    /** Échange un code d'enrôlement contre l'identité d'un appareil. */
    EnrollResponse enroler(EnrollRequest requete);

    /** Ouvre une session depuis un appareil enrôlé, déverrouillé par son PIN. */
    MobileLoginResponse connecter(MobileLoginRequest requete);

    /** Renouvelle une session mobile à partir de son jeton de rafraîchissement. */
    MobileLoginResponse rafraichir(String refreshToken);

    /** Pose ou remplace le code PIN de l'utilisateur courant. */
    void definirPin(UUID userId, String pin);

    /** Coupe un appareil sans effacer la trace des actes qu'il a portés. */
    void revoquer(UUID deviceId, UUID auteurId, UUID branchId);

    List<DeviceResponse> listerAppareils(UUID branchId);
}

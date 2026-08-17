package com.labo.anapath.mobile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Objets de transfert de l'authentification mobile, regroupés : ils sont courts,
 * n'ont de sens qu'ensemble, et les éparpiller sur huit fichiers rendrait le
 * protocole plus difficile à lire qu'à écrire.
 */
public final class MobileDtos {

    private MobileDtos() {
    }

    /**
     * Demande d'enrôlement d'un appareil.
     *
     * <p>L'utilisateur se désigne par son adresse : le code est haché en base et
     * ne peut donc pas servir de clé de recherche. Aucun secret n'est employé
     * comme identifiant, ici comme ailleurs dans cette chaîne.</p>
     */
    public record EnrollRequest(
            @NotBlank @Email String email,
            @NotBlank String code,
            @NotBlank @Size(max = 120) String label,
            /** Clé publique de l'enclave sécurisée, encodée en base64. */
            @NotBlank String publicKey) {
    }

    public record EnrollResponse(UUID deviceId, String label, LocalDateTime enrolledAt, boolean pinDefini) {
    }

    /**
     * Connexion depuis un appareil déjà enrôlé.
     *
     * <p>L'appareil prouve la possession, le PIN la connaissance. Le PIN seul
     * n'ouvre rien : il faut aussi le téléphone, que le serveur peut révoquer.</p>
     */
    public record MobileLoginRequest(
            @NotNull UUID deviceId,
            @NotBlank String pin) {
    }

    /**
     * Réponse de connexion mobile.
     *
     * <p>Les jetons figurent dans le corps, contrairement à {@code LoginResponse}
     * du web qui les confie à des cookies HttpOnly. Un cookie protège d'un vol
     * par script dans un navigateur ; il n'y a pas de navigateur ici, et
     * l'application doit pouvoir ranger le jeton dans le trousseau du système.</p>
     *
     * <p>{@code permissions} évite un aller-retour : l'application s'en sert pour
     * n'afficher que les opérations autorisées. Le filtrage reste une commodité
     * d'affichage — l'autorisation, elle, est vérifiée à chaque appel.</p>
     */
    public record MobileLoginResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            UUID userId,
            String nomComplet,
            /**
             * Branche de rattachement, à replacer dans l'en-tête {@code X-Branch-Id}
             * de chaque appel — le serveur répond 428 sans elle. Le web la fait
             * choisir sur un écran dédié ; au comptoir ce serait une étape de trop,
             * l'agent travaillant toujours depuis le même site.
             */
            UUID branchId,
            List<String> permissions) {
    }

    /**
     * Renouvellement d'une session mobile.
     *
     * <p>Le jeton voyage dans le corps, là où le web le confie à un cookie
     * HttpOnly : l'application n'a pas de navigateur, elle range son jeton dans
     * le trousseau du système et doit pouvoir le présenter explicitement.</p>
     */
    public record MobileRefreshRequest(
            @NotBlank String refreshToken,
            /**
             * Appareil qui renouvelle. Sans lui, le jeton reconduit perdrait sa
             * provenance mobile — et avec elle l'obligation de signer les
             * validations. Il suffirait alors d'attendre un renouvellement pour
             * retomber au niveau de garantie du web.
             */
            @NotNull UUID deviceId) {
    }

    /**
     * Tout ce qu'il faut remettre à un agent pour qu'il mette son téléphone en
     * service : son code d'enrôlement et son code PIN.
     *
     * <p>Les deux n'existent en clair qu'ici, dans cette unique réponse — la base
     * n'en garde que les empreintes. L'administrateur doit donc les transmettre
     * aussitôt ; les retrouver plus tard est impossible, il faudra en régénérer.</p>
     *
     * <p>Le PIN est engendré par le serveur plutôt que choisi par l'agent :
     * autrement, un appareil fraîchement enrôlé devrait ouvrir une session pour
     * poser son code, alors qu'ouvrir une session exige déjà d'en avoir un.
     * L'agent pourra le changer une fois connecté.</p>
     */
    public record AccesMobileResponse(
            UUID userId,
            String nomComplet,
            String codeEnrolement,
            LocalDateTime codeExpireLe,
            String pin) {
    }

    /** État de l'accès mobile d'un utilisateur, pour l'écran d'administration. */
    public record EtatAccesResponse(
            UUID userId,
            boolean acces,
            boolean pinDefini,
            List<DeviceResponse> appareils) {
    }

    /** Création d'un code d'enrôlement par un administrateur. */
    public record EnrollmentCodeRequest(@NotNull UUID userId) {
    }

    /**
     * Le code en clair n'apparaît qu'ici, une seule fois — il n'est stocké que
     * haché. L'administrateur doit donc le transmettre immédiatement.
     */
    public record EnrollmentCodeResponse(String code, LocalDateTime expiresAt) {
    }

    /** Pose ou remplacement du PIN par son porteur. */
    public record PinRequest(
            @NotBlank @Pattern(regexp = "\\d{4,8}", message = "Le code PIN doit comporter de 4 à 8 chiffres.")
            String pin) {
    }

    public record DeviceResponse(
            UUID id,
            UUID userId,
            String label,
            LocalDateTime enrolledAt,
            LocalDateTime lastSeenAt,
            LocalDateTime revokedAt) {

        static DeviceResponse de(MobileDevice d) {
            return new DeviceResponse(d.getId(), d.getUserId(), d.getLabel(),
                    d.getEnrolledAt(), d.getLastSeenAt(), d.getRevokedAt());
        }

        static List<DeviceResponse> de(List<MobileDevice> devices) {
            return devices.stream().map(DeviceResponse::de).toList();
        }
    }
}

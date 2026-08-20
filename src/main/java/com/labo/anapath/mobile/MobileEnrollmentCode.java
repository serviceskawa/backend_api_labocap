package com.labo.anapath.mobile;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Code d'enrôlement à usage unique, délivré par un administrateur.
 *
 * <p>C'est le seul point d'entrée ouvert de toute la chaîne mobile : il échange
 * un secret remis de la main à la main contre les identifiants propres à un
 * appareil. Il est donc court-vivant et à usage unique.</p>
 *
 * <p>Le code est haché, donc introuvable par recherche directe. L'enrôlement
 * demande aussi l'adresse de l'utilisateur : on retrouve ses codes vivants, puis
 * on les vérifie un à un. C'est délibéré — l'utilisateur se désigne, ici comme à
 * la connexion, et aucun secret ne sert jamais d'identifiant.</p>
 */
@Entity
@Table(name = "mobile_enrollment_codes")
@Getter
@Setter
@NoArgsConstructor
public class MobileEnrollmentCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    /**
     * Échéance héritée : nulle pour les codes délivrés depuis que la validité
     * tient à la révocation seule.
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * Le code scellé, pour pouvoir remontrer le QR.
     *
     * <p>Chiffré (AES-256-GCM, clé applicative), jamais en clair : une copie de
     * la base ne suffit pas à le lire. L'empreinte ci-dessus reste la référence
     * qui valide un enrôlement ; cette colonne ne sert qu'à l'affichage.</p>
     *
     * <p>Nulle sans clé configurée, et pour les codes antérieurs : ils enrôlent
     * encore, mais ne se réaffichent pas.</p>
     */
    @Column(name = "code_chiffre", length = 512)
    private String codeChiffre;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    /** Combien d'appareils ce code a enrôlés. */
    @Column(name = "used_count", nullable = false)
    private int usedCount;

    /** Premier usage. Conservé pour dire depuis quand le code circule. */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

    /** Le dernier appareil enrôlé par ce code. */
    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public MobileEnrollmentCode(UUID userId, String codeHash, LocalDateTime expiresAt, UUID createdBy) {
        this.userId = userId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdBy = createdBy;
    }

    /**
     * Utilisable tant qu'il n'est pas révoqué.
     *
     * <p>Il s'éteignait au premier appareil enrôlé. Un agent qui changeait de
     * téléphone, ou dont l'installation échouait, devait faire rouvrir son
     * accès — pour un code que rien n'obligeait à être à usage unique, la
     * révocation existant déjà.</p>
     */
    public boolean estUtilisable() {
        return revokedAt == null
                && (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }

    /** Note un enrôlement de plus, sans effacer la trace du premier. */
    public void noterUnUsage(UUID appareilId) {
        if (usedAt == null) usedAt = LocalDateTime.now();
        this.deviceId = appareilId;
        usedCount++;
    }

    public void revoquer(UUID auteurId) {
        revokedAt = LocalDateTime.now();
        revokedBy = auteurId;
    }
}

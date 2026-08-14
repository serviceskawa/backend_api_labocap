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

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * Marque l'usage plutôt que de supprimer la ligne : on veut pouvoir dire
     * quel code a enrôlé quel appareil, et quand.
     */
    @Column(name = "used_at")
    private LocalDateTime usedAt;

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

    public boolean estUtilisable() {
        return usedAt == null && expiresAt.isAfter(LocalDateTime.now());
    }
}

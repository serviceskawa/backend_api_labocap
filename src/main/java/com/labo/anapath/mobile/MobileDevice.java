package com.labo.anapath.mobile;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Téléphone enrôlé pour l'application mobile de signature.
 *
 * <p>L'application n'embarque aucune clé d'API : un secret livré dans un binaire
 * s'en extrait, et ne peut donc porter aucune autorisation. Chaque appareil est
 * enrôlé séparément et parle ensuite avec sa propre identité — un téléphone
 * perdu se révoque seul, sans toucher aux autres.</p>
 *
 * <p>{@link #publicKey} reçoit la clé publique produite par l'enclave sécurisée
 * du téléphone ; la clé privée n'en sort jamais et signera la validation
 * médicale. C'est ce qui donne une non-répudiation que le web n'a pas, où une
 * session ouverte suffit à valider un diagnostic.</p>
 */
@Entity
@Table(name = "mobile_devices")
@Getter
@Setter
@NoArgsConstructor
public class MobileDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "branch_id")
    private UUID branchId;

    /** Nom lisible, pour que l'administrateur sache ce qu'il révoque. */
    @Column(nullable = false, length = 120)
    private String label;

    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    @Column(name = "enrolled_at", nullable = false)
    private LocalDateTime enrolledAt;

    /** Dernière connexion réussie — repère un appareil oublié ou volé. */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    /**
     * Date de révocation, et non suppression de la ligne : couper un appareil
     * ne doit pas effacer la trace des actes qu'il a portés.
     */
    /**
     * Le jeton de notification de cet appareil.
     *
     * <p>Porté par l'appareil et non par la personne : un médecin peut avoir
     * deux téléphones, et un téléphone changer de main. Révoquer l'appareil
     * éteint ses notifications du même geste.</p>
     *
     * <p>Nul tant que l'application ne l'a pas transmis — un appareil enrôlé
     * avant que les notifications n'existent, ou un système qui les refuse.</p>
     */
    @Column(name = "push_token", length = 512)
    private String pushToken;

    /** De quand date le jeton qu'on garde. */
    @Column(name = "push_token_at")
    private LocalDateTime pushTokenAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public MobileDevice(UUID userId, UUID branchId, String label, String publicKey) {
        this.userId = userId;
        this.branchId = branchId;
        this.label = label;
        this.publicKey = publicKey;
        this.enrolledAt = LocalDateTime.now();
    }

    public boolean estActif() {
        return revokedAt == null;
    }
}

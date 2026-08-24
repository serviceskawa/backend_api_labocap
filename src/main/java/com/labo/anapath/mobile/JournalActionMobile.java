package com.labo.anapath.mobile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un acte accompli depuis l'application mobile.
 *
 * <h2>Ce qu'on garde</h2>
 *
 * <p>Qui, depuis quel appareil, quoi, quand, et avec quelle issue. La méthode et
 * le chemin suffisent à dire la nature de l'acte — {@code POST
 * /reports/{id}/store-signature} est une remise, {@code POST
 * /test-orders/{id}/images} un ajout de cliché — et l'identifiant dans le
 * chemin désigne la ligne touchée.</p>
 *
 * <h2>Ce qu'on ne garde pas, délibérément</h2>
 *
 * <p>Le corps des requêtes. Il porte des contenus médicaux, des images de
 * signature manuscrite et des codes PIN. Un journal qui les recopierait
 * deviendrait le fichier le plus sensible de la base, pour un gain nul : on
 * cherche à savoir qui a fait quoi, pas à rejouer ce qui a été fait.</p>
 */
@Entity
@Table(name = "mobile_action_logs")
@Getter
@Setter
@NoArgsConstructor
public class JournalActionMobile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * L'appareil d'où vient l'acte.
     *
     * <p>Nul ne devrait pas arriver — le journal ne s'écrit que pour les
     * requêtes portant une revendication d'appareil — mais la colonne le
     * tolère : perdre une ligne de journal pour une contrainte serait perdre
     * précisément ce qu'on cherchait à conserver.</p>
     */
    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "methode", nullable = false, length = 10)
    private String methode;

    @Column(name = "chemin", nullable = false, length = 500)
    private String chemin;

    /** Le code de réponse. Un refus se journalise comme une réussite : on veut aussi les tentatives. */
    @Column(name = "statut", nullable = false)
    private int statut;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    public JournalActionMobile(UUID userId, UUID deviceId, UUID branchId,
                               String methode, String chemin, int statut) {
        this.userId = userId;
        this.deviceId = deviceId;
        this.branchId = branchId;
        this.methode = methode;
        this.chemin = chemin.length() > 500 ? chemin.substring(0, 500) : chemin;
        this.statut = statut;
        this.occurredAt = LocalDateTime.now();
    }
}

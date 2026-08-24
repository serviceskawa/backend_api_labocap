package com.labo.anapath.discussion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Quelqu'un qui participe au fil d'un dossier.
 *
 * <p>Le rôle est figé au moment où la personne rejoint le fil : il dit sous
 * quelle casquette elle a parlé ce jour-là. Le relire depuis ses rôles actuels
 * ferait changer l'attribution d'un message ancien le jour d'une promotion — et
 * sur un dossier médical, qui a dit quoi et à quel titre ne se réécrit pas.</p>
 */
@Entity
@Table(name = "discussion_participants")
@Getter
@Setter
@NoArgsConstructor
public class DiscussionParticipant {

    /** Les deux casquettes que la maquette distingue. */
    public static final String MEDECIN = "medecin";
    public static final String TECHNICIEN = "technicien";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discussion_id", nullable = false)
    private Discussion discussion;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt = LocalDateTime.now();

    public DiscussionParticipant(Discussion discussion, UUID userId, String role) {
        this.discussion = discussion;
        this.userId = userId;
        this.role = role;
    }
}

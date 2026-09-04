package com.labo.anapath.discussion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Le fil de discussion d'un dossier.
 *
 * <p>Un fil par dossier, jamais par personne : la conversation appartient au
 * cas. Deux médecins qui se relaient sur une demande retrouvent le même fil, et
 * celui qui arrive lit ce qui a précédé — ce qu'un échange téléphonique ne
 * permet pas.</p>
 *
 * <p>Le fil naît à la première ouverture, même sans message : la maquette veut
 * qu'un médecin puisse écrire le premier au technicien.</p>
 */
@Entity
@Table(name = "discussions")
@Getter
@Setter
@NoArgsConstructor
public class Discussion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "test_order_id", nullable = false, unique = true)
    private UUID testOrderId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "discussion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("addedAt ASC")
    private List<DiscussionParticipant> participants = new ArrayList<>();

    public Discussion(UUID testOrderId, UUID branchId) {
        this.testOrderId = testOrderId;
        this.branchId = branchId;
    }
}

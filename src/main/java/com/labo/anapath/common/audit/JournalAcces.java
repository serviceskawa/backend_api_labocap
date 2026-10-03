package com.labo.anapath.common.audit;

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
 * Une consultation : qui a lu (ou téléchargé) quoi, quand, d'où (lot 6).
 *
 * <p>Ajout seul : aucune route ne modifie ni ne supprime une ligne ; la purge
 * mensuelle ({@link PurgeDesJournaux}) est la seule à y toucher.</p>
 */
@Entity
@Table(name = "journal_acces")
@Getter
@Setter
@NoArgsConstructor
public class JournalAcces {

    public enum Action { READ, DOWNLOAD, PRINT, EXPORT }
    public enum Entite { PATIENT, TEST_ORDER, REPORT, FILE, INVOICE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "at", nullable = false, updatable = false)
    private LocalDateTime at = LocalDateTime.now();

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "branch_id", updatable = false)
    private UUID branchId;

    @Column(name = "action", nullable = false, updatable = false, length = 20)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Action action;

    @Column(name = "entity_type", nullable = false, updatable = false, length = 20)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Entite entityType;

    /** Identifiant de l'entité, ou chemin relatif pour un fichier. */
    @Column(name = "entity_id", nullable = false, updatable = false, length = 255)
    private String entityId;

    @Column(name = "ip", updatable = false, length = 64)
    private String ip;

    @Column(name = "user_agent_hash", updatable = false, length = 64)
    private String userAgentHash;
}

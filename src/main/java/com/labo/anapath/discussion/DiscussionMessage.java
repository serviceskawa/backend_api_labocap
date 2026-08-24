package com.labo.anapath.discussion;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un message du fil.
 *
 * <p>Texte, photo d'une lame ou note vocale. Une seule colonne pour les trois :
 * ils s'excluent, et trois colonnes dont deux toujours vides diraient le
 * contraire.</p>
 *
 * <p>{@code taggedUserId} nomme un destinataire précis. Nul, le message
 * s'adresse au groupe — ce qui ne veut pas dire à personne : les techniciens en
 * charge du dossier le reçoivent de toute façon.</p>
 */
@Entity
@Table(name = "discussion_messages")
@Getter
@Setter
@NoArgsConstructor
public class DiscussionMessage {

    public static final String TEXTE = "texte";
    public static final String PHOTO = "photo";
    public static final String AUDIO = "audio";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discussion_id", nullable = false)
    private Discussion discussion;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "type", nullable = false, length = 10)
    private String type = TEXTE;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "tagged_user_id")
    private UUID taggedUserId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public DiscussionMessage(Discussion discussion, UUID authorId, String type,
                             String content, UUID taggedUserId) {
        this.discussion = discussion;
        this.authorId = authorId;
        this.type = type;
        this.content = content;
        this.taggedUserId = taggedUserId;
    }
}
